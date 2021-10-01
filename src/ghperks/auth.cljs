(ns ghperks.auth
  (:require
    [promesa.core :as p]
    [applied-science.js-interop :as j]
    ["passport" :as passport]
    ["passport-github2" :as ghpass]
    [sitefox.util :refer [env-required env]]
    [sitefox.db :refer [kv]]
    [sitefox.mail :refer [transport send-mail]]))

(defn setup []
  (.use passport
        (ghpass/Strategy. #js {:clientID (env-required "GHPERKS_GH_CLIENT_ID")
                               :clientSecret (env-required "GHPERKS_GH_CLIENT_SECRET")
                               :callbackURL "http://localhost:8000/auth/github/callback"}
                          (fn [accessToken refreshToken profile done]
                            (done nil profile))))
  (j/call passport :serializeUser (fn [user done] (done nil user)))
  (j/call passport :deserializeUser (fn [user done] (done nil user)))
  passport)

(defn setup-routes [app]
  (.use app (.initialize passport))
  (.use app (.session passport))
  (.get app "/auth/github"
        (j/call passport :authenticate "github" (clj->js {:scope ["user:email"]})))
  (.get app "/auth/logout"
        (fn [req res]
          (j/call req :logout)
          (j/call-in req [:session :destroy])
          (.redirect res "/")))
  (.get app "/auth/github/callback"
        (j/call passport :authenticate "github" (clj->js {:failureRedirect "/auth/error"}))
        (fn [req res]
          (p/let [id (j/get-in req [:user :id])
                  username (j/get-in req [:user :username])
                  sign-ups-db (-> (kv "pre-sign-up")
                                  (.set (str "gh-" id)
                                        (clj->js {:timestamp (js/Date.)
                                                  :user (aget req "user")})))
                  email-address (env "EMAIL_NOTIFY_ADDRESS")
                  mail (transport)
                  sent (when email-address
                         (send-mail mail
                                    email-address email-address
                                    "GH Perks signup" "" (str "New signup: @" username)))]
            (js/console.log sent)
            (.redirect res "/hello"))))
  (.get app "/auth/error"
        (fn [req res] (.send res "Authentication error."))))

