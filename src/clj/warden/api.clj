(ns warden.api
  (:use compojure.core)
  (:require [warden.config :refer (config)]
            [warden.supervisord :as super]
            [liberator.core :refer (defresource resource)]
            [liberator.representation :refer (render-map-generic render-seq-generic)]
            [tailrecursion.cljson :refer (clj->cljson)]))

;; helper fns
(defn supervisor-id [{:keys [host name port]}]
  "id for referencing a particular supervisor server"
  (str host "-" port "-" name))

(defn key= [x y]
  "Checks that every key in x is equal in y"
  (when (every? true? (for [[k v] x] (= v (get y k)))) y))

(defn filter-key= [m ms]
  "filters a collection of maps by those that match a minimum keyset"
  (filter (partial key= m) ms))

(defn some-key= [m ms]
  "select by minimum keyset"
  (some (partial key= m) ms))

;; GLOBALS WHAT ARE THESE DOING HERE????
(def supervisor-clients (super/supervisor-clients (:hosts config)))
(def supervisors-atom (super/sync-supervisors! supervisor-clients 1000))
(defn read-supervisors [] (get @supervisors-atom :supervisors))

;; Liberator Resource Definitions
(defmethod render-map-generic
  "application/cljson"
  [m ctx] (clj->cljson m))

(defmethod render-seq-generic
  "application/cljson"
  [s ctx] (clj->cljson s))

(defresource supervisors-all []
  :available-media-types ["application/json""application/edn" "application/cljson"]
  :allowed-methods [:get]
  :exists? (fn [ctx]
             (let [s (read-supervisors)]
               (when (seq s) {::supervisors s})))
  :handle-ok ::supervisors)

(defresource supervisors-group [host]
  :available-media-types ["application/json" "application/edn" "application/cljson"]
  :allowed-methods [:get]
  :exists? (fn [ctx]
             (let [s (filter-key= {:host host} (read-supervisors))]
               (when (seq s) {::supervisors s})))
  :handle-ok ::supervisors)

(defresource supervisor [host name]
  :available-media-types ["application/json""application/edn" "application/cljson"]
  :allowed-methods [:get]
  :exists? (fn [ctx]
             (if-let [s (some-key= {:host host :name name} (read-supervisors))]
               {::supervisor s}))
  :handle-ok ::supervisor)

(defresource supervisor-processes [host name])
(defresource supervisor-process [host name process])

(defresource supervisor-process-action [host name process action]
  :allowed-methods [:post]
  :available-media-types ["application/json" "application/edn" "application/cljson"]
  :exists? (fn [ctx]
            (let [c (:client (some-key= {:host host :name name} supervisor-clients))
                  f (super/api (keyword action))]
              (if (and c f) {::client c ::action f})))
  :post-to-missing? false
  :post! (fn [ctx]
           (let [client (::client ctx) action (::action ctx)]
             {::creates {:result (action client process)}}))
  :handle-created ::creates)

;; Compojure Route Definitions
(defroutes api-routes
  ;; list of all supervisors
  (ANY "/supervisors" []
    (supervisors-all))
  ;; list of supervisors on a host
  (ANY "/supervisors/:host" [host]
    (supervisors-group host))
  ;; a particular supervisor on a host
  (ANY "/supervisors/:host/:name" [host name]
    (supervisor host name))
  ;; a process list of a supervisor on a host
  (ANY "/supervisors/:host/:name/processes" [host name]
    (supervisor-processes host name))
  ;; detail about a particular process
  (ANY "/supervisors/:host/:name/processes/:process" [host name process]
    (supervisor-process host name process))
  ;; action enacted on a particular process
  (ANY "/supervisors/:host/:name/processes/:process/action/:action" [host name process action]
    (supervisor-process-action host name process action)))
