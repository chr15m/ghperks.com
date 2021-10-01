(ns ghperks.auth
  (:require
    [promesa.core :as p]
    [applied-science.js-interop :as j]
    ["passport" :as passport]
    ["passport-github2" :as ghpass]
    ["node-fetch" :as fetch]
    [sitefox.util :refer [env-required env]]
    [sitefox.db :refer [kv]]
    [sitefox.mail :refer [transport send-mail]]))

(defn get-primary-email [user]
  (let [emails (-> user
                   (aget "all_emails")
                   (js->clj :keywordize-keys true))]
    (->> emails
         (filter :primary)
         first
         :email)))

(defn setup []
  (.use passport
        (ghpass/Strategy. #js {:clientID (env-required "GHPERKS_GH_CLIENT_ID")
                               :clientSecret (env-required "GHPERKS_GH_CLIENT_SECRET")
                               :callbackURL (str (if (env "NGINX_SERVER_NAME")
                                                   (str "https://" (env "NGINX_SERVER_NAME"))
                                                   "http://localhost:8000")
                                                 "/auth/github/callback")}
                          (fn [accessToken refreshToken profile done]
                            (when profile
                              (aset profile "accessToken" accessToken)
                              (aset profile "refreshToken" refreshToken))
                            (done nil profile))))
  (j/call passport :serializeUser (fn [user done] (done nil user)))
  (j/call passport :deserializeUser (fn [user done] (done nil user)))
  passport)

(defn setup-routes [app]
  (.use app (.initialize passport))
  (.use app (.session passport))
  (.get app "/auth/github"
        (j/call passport :authenticate "github"
                (clj->js {:scope ["user:email" "user:read"]
                          :accessType "offline"
                          :approvalPrompt "force"})))
  (.get app "/auth/logout"
        (fn [req res]
          (j/call req :logout)
          (j/call-in req [:session :destroy])
          (.redirect res "/")))
  (.get app "/auth/github/callback"
        (j/call passport :authenticate "github" (clj->js {:failureRedirect "/auth/error"}))
        (fn [req res]
          ; (js/console.log (aget req "user"))
          (p/let [id (j/get-in req [:user :id])
                  username (j/get-in req [:user :username])
                  emails (-> (fetch "https://api.github.com/user/emails"
                                    (clj->js {:headers {"Accept" "application/vnd.github.v3+json"
                                                        "Authorization" (str "token " (j/get-in req [:user :accessToken]))}}))
                             (.then #(.json %)))
                  user-signup-data {:timestamp (js/Date.)
                                    :emails emails
                                    :user (aget req "user")}
                  sign-ups-db (-> (kv "pre-sign-up")
                                  (.set (str "gh-" id)
                                        (clj->js user-signup-data)))
                  email-address (env "EMAIL_NOTIFY_ADDRESS")
                  mail (transport)
                  sent (when email-address
                         (send-mail mail
                                    email-address email-address
                                    "GH Perks signup" "" (str "New signup: @" username)))]
            (j/assoc-in! req [:user :all_emails] emails)
            (js/console.log sent)
            (.redirect res "/hello"))))
  (.get app "/auth/error"
        (fn [req res] (.send res "Authentication error."))))

