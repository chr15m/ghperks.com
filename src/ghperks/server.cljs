(ns ghperks.server
  (:require
    [sitefox.web :as web]
    [sitefox.util :refer [env reloader]]
    [promesa.core :as p]))

(defn setup-routes [app]
  (web/reset-routes app)
  (web/static-folder app "/" (if (env "NGINX_SERVER_NAME") "build" "public")))

(defn main! []
  (p/let [[app host port] (web/start)]
    (reloader (partial #'setup-routes app))
    (setup-routes app)
    (println "main!")))
