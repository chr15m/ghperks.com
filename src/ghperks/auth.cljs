(ns ghperks.auth
  (:require
    [applied-science.js-interop :as j]
    ["passport" :as passport]
    ["passport-github2" :as ghpass]
    [sitefox.util :refer [env-required]]))

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
  (.use app (.session passport)))

