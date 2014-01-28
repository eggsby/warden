(ns warden.api
  (:use compojure.core)
  (:require [warden.config :refer (config)]
            [warden.supervisord :as super]
            [liberator.core :refer (defresource resource)]
            [liberator.representation :refer (render-map-generic render-seq-generic)]
            [tailrecursion.cljson :refer (clj->cljson)]
            [clojure.core.match :refer (match)]))

;; helper fns
(defn supervisor-id [{:keys [host name port]}]
  "id for referencing a particular supervisor server"
  (str host "-" port "-" name))

(defn key= [x y]
  "Checks that every key in x is equal in y"
  (if (every? true? (for [[k v] x] (= v (get y k)))) y))

;; Liberator Resource Definitions
(defmethod render-map-generic
  "application/cljson"
  [m ctx] (clj->cljson m))

(defmethod render-seq-generic
  "application/cljson"
  [s ctx] (clj->cljson s))

(def supervisor-clients
  (super/supervisor-clients (:hosts config)))

(defresource supervisors-all []
  :available-media-types ["application/json""application/edn" "application/cljson"]
  :allowed-methods [:get]
  :handle-ok (fn [ctx] (super/get-supervisors supervisor-clients)))

(defresource supervisors-group [host]
  :available-media-types ["application/json" "application/edn" "application/cljson"]
  :allowed-methods [:get]
  :exists? (fn [ctx]
             (let [cs (filter #(key= {:host host} %) supervisor-clients)]
               (if-let [s (super/get-supervisors cs)] {::supervisors s})))
  :handle-ok ::supervisors)

(defresource supervisor [host name]
  :available-media-types ["application/json""application/edn" "application/cljson"]
  :allowed-methods [:get]
  :exists? (fn [ctx]
             (let [c (some #(key= {:host host :name name} %) supervisor-clients)]
               (if-let [s (super/get-supervisor c)] {::supervisor s})))
  :handle-ok ::supervisor)

(defn supervisor-processes [host name])
(defn supervisor-process [host name process])

(defn supervisor-process-action [host name process action]
  :allowed-methods [:post]
  :available-media-types ["application/json" "application/edn" "application/cljson"]
  :post! (fn [ctx]
           (let [{c :client} (some #(key= {:host host :name name} %) supervisor-clients)
                 f (get super/api (keyword action))]
             (if (and c f) {::result (f c process)})))
  :handle-created ::result)

;; Compojure Route Definitions

(defroutes api-routes
  (ANY "/supervisors" []
    (supervisors-all))
  (ANY "/supervisors/:host" [host]
    (supervisors-group host))
  (ANY "/supervisors/:host/:name" [host name]
    (supervisor host name))
  (ANY "/supervisors/:host/:name/processes" [host name]
    (supervisor-processes host name))
  (ANY "/supervisors/:host/:name/processes/:process" [host name process]
    (supervisor-process host name process))
  (ANY "/supervisors/:host/:name/processes/:process/action/:action" [host name process action]
    (supervisor-process-action host name process action)))
