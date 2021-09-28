(ns ghperks.server
  (:require
    [promesa.core :as p]
    [applied-science.js-interop :as j]
    [sitefox.web :as web]
    [sitefox.util :refer [env reloader]]
    [ghperks.auth :as auth]))

;*** set up gh passport auth ***;

(defn setup-routes [app passport]
  (web/reset-routes app)
  (web/static-folder app "/" (if (env "NGINX_SERVER_NAME") "build" "public"))
  (auth/setup-routes app)
  (.get app "/hello"
        (fn [req res] (.send res (str "Hello " (j/get-in req [:user :displayName])
                                      " "
                                      (j/get-in req [:user :id])
                                      " "
                                      (j/get-in req [:user :emails 0 :value])))))
  (.get app "/auth/error"
        (fn [req res] (.send res "Authentication error.")))
  (.get app "/auth/github"
        (j/call passport :authenticate "github" (clj->js {:scope ["user:email"]})))
  (.get app "/auth/github/callback"
        (j/call passport :authenticate "github" (clj->js {:failureRedirect "/auth/error"}))
        (fn [req res] (.redirect res "/hello"))))

(defn main! []
  (p/let [passport (auth/setup)
          [app host port] (web/start)]
    (reloader (partial #'setup-routes app passport))
    (setup-routes app passport)
    (println "main!")))
