(ns ghperks.server
  (:require
    [promesa.core :as p]
    [applied-science.js-interop :as j]
    [shadow.resource :as rc]
    [sitefox.web :as web]
    [sitefox.util :refer [env reloader]]
    [ghperks.auth :as auth]))

;*** set up gh passport auth ***;

(defn setup-routes [app passport]
  (web/reset-routes app)
  (auth/setup-routes app)
  (.get app "/" (fn [req res]
                  (if (j/get-in req [:user :displayName])
                    (.send res (str "Hello " (j/get-in req [:user :displayName])
                                    " "
                                    (j/get-in req [:user :id])
                                    " "
                                    (j/get-in req [:user :emails 0 :value])
                                    " "
                                    "<a href='/auth/logout'>logout</a>"
                                    ))
                    (.send res (rc/inline "index.html")))))
  (.get app "/auth/github"
        (j/call passport :authenticate "github" (clj->js {:scope ["user:email"]})))
  (.get app "/auth/logout"
        (fn [req res]
          (j/call req :logout)
          (j/call-in req [:session :destroy])
          (.redirect res "/")))
  (.get app "/auth/error"
        (fn [req res] (.send res "Authentication error.")))
  (.get app "/auth/github/callback"
        (j/call passport :authenticate "github" (clj->js {:failureRedirect "/auth/error"}))
        (fn [req res] (.redirect res "/")))
  (web/static-folder app "/" (if (env "NGINX_SERVER_NAME") "build" "public")))

(defn main! []
  (p/let [passport (auth/setup)
          [app host port] (web/start)]
    (reloader (partial #'setup-routes app passport))
    (setup-routes app passport)
    (println "main!")))
