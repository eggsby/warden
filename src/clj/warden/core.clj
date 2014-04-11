(ns warden.core
  (:require [warden.handler :refer [app]]
            [org.httpkit.server :refer [run-server]]))

(defn -main [& args]
  (run-server app {:port 8080}))
           