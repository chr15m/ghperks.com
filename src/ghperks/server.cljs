(ns ghperks.server
  (:require
    [promesa.core :as p]
    [applied-science.js-interop :as j]
    [reagent.dom.server :refer [render-to-static-markup]]
    [shadow.resource :as rc]
    [sitefox.web :as web]
    [sitefox.util :refer [env reloader]]
    ["node-html-parser" :as html-parser]
    [ghperks.auth :as auth]))

(def r render-to-static-markup)
(def index-html (rc/inline "index.html"))

(defn replace-content [template replacement]
  (let [main (.querySelector template "main")
        login-link (.querySelector template "#login-button")]
    (j/call main :set_content replacement)
    (j/call login-link :remove)
    template))

(defn logged-in-view [req]
  [:section.ui-section-content
   [:div.ui-layout-container
    [:p "Hello " (j/get-in req [:user :displayName]) "! (" (j/get-in req [:user :id]) ")"]
    [:p (j/get-in req [:user :emails 0 :value])]
    [:p [:a {:href "/auth/logout"} "logout"]]]])

(defn home-page [req res]
  (let [template (html-parser/parse index-html)
        user (j/get req :user)]
    (if user
      (.send res
             (->
               template
               (replace-content (r (logged-in-view req)))
               .toString))
      (.send res index-html))))

(defn setup-routes [app passport]
  (web/reset-routes app)
  (auth/setup-routes app)
  (.get app "/" home-page)
  (web/static-folder app "/" (if (env "NGINX_SERVER_NAME") "build" "public")))

(defn main! []
  (p/let [passport (auth/setup)
          [app host port] (web/start)]
    (reloader (partial #'setup-routes app passport))
    (setup-routes app passport)
    (println "main!")))
