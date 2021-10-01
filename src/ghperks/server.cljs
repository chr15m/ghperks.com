(ns ghperks.server
  (:require
    [promesa.core :as p]
    [applied-science.js-interop :as j]
    [reagent.dom.server :refer [render-to-static-markup]]
    [shadow.resource :as rc]
    [sitefox.web :as web]
    [sitefox.util :refer [env reloader bind-console-log-to-file]]
    ["node-html-parser" :as html-parser]
    [ghperks.auth :as auth]))

(bind-console-log-to-file)

(def r render-to-static-markup)
(def index-html (rc/inline "index.html"))

(defn replace-login-link [template frag]
  (let [login-link (.querySelector template "#login-button")]
    (j/call login-link :replaceWith frag)
    template))

(defn replace-content [template replacement]
  (let [main (.querySelector template "main")]
    (j/call main :set_content replacement)
    template))

(defn logged-in-view [req]
  [:section.ui-section-hero
   [:div.ui-layout-container
    (if (aget req "user")
      [:div
       [:p.ui-text-intro "Hello @" (j/get-in req [:user :username]) "!"]
       (let [email (j/get-in req [:user :emails 0 :value])]
         (if email
           [:div
            [:p "Thank you for signing up for the GH Perks beta."]
            [:p "I'll send you an email at " [:strong email] " when I launch."]
            [:p "See you soon."]
            [:br]
            [:p "P.S. Sponsor me to support development & lock in your place at launch:"]
            [:div {:class "ui-component-cta ui-layout-flex"}
             [:a {:href (str "https://github.com/sponsors/" (env "GHPERKS_OWNER") "/sponsorships?sponsor=" (env "GHPERKS_OWNER") "&tier_id=" (env "GHPERKS_TIER"))
                  :target "_BLANK"
                  :class "ui-component-button ui-component-button-small ui-component-button-primary"}
              "Sponsor me on GitHub"]]]
           [:div
            [:p "You don't have an email address configured with GitHub."]
            [:p "You'll have to check back in a couple of weeks."]
            [:p "Or follow " [:a {:href "https://twitter.com/mccrmx"} "@mccrmx"] " to find out about the release."]
            [:br]
            [:p "See you soon."]]))]
      [:div
       [:p.ui-text-intro
        "To be notified of the launch please sign in."]
       [:div {:class "ui-component-cta ui-layout-flex"}
        [:a {:href "/auth/github"
             :class "ui-component-button ui-component-button-small ui-component-button-primary"}
         "Sign in with GitHub"]]])]])

(defn home-page [req res]
  (let [template (html-parser/parse index-html)
        user (j/get req :user)]
    (if user
      (.send res
             (->
               template
               (replace-login-link (r [:span "You're signed up."]))
               .toString))
      (.send res index-html))))

(defn auth-done-message [req res]
  (let [template (html-parser/parse index-html)
        user (j/get req :user)]
    (.send res
           (-> template
               (replace-login-link "")
               (replace-content (r (logged-in-view req)))
               .toString))))

(defn setup-routes [app passport]
  (web/reset-routes app)
  (auth/setup-routes app)
  (.get app "/hello" auth-done-message)
  (.get app "/" home-page)
  (web/static-folder app "/" (if (env "NGINX_SERVER_NAME") "build" "public")))

(defn main! []
  (p/let [passport (auth/setup)
          [app host port] (web/start)]
    (reloader (partial #'setup-routes app passport))
    (setup-routes app passport)
    (println "main!")))
