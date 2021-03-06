(ns warden.components.processes
  (:require [warden.net :refer (cljson-post)]
            [warden.util :refer (some-key=)]
            [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [tailrecursion.cljson :refer [cljson->clj]]
            [chord.client :refer [ws-ch]]
            [cljs.core.async :refer (<! close! chan put! alts!)])
  (:require-macros [cljs.core.async.macros :refer (go go-loop)]))

(defn handle-error! [ch response]
  "Handle responses from the server for POSTing an action"
  (case (:status response)
    201 (if-let [err (some-> (update-in response [:body] cljson->clj)
                             (get-in [:body :result :fault-string]))]
          (put! ch [:error err]))
    404 (if-let [err (:body response)] (put! ch [:error err]))))

(defn handle-action! [ch name action url]
  "Handle a process action from the user"
  (go
   (let [message (str "sent: " action " " name)]
     (put! ch [:message message])
     (handle-error! ch (<! (cljson-post url))))))

(defn supervisor-api [{:keys [host name]}]
  (str "/api/supervisors/" host "/" name))

(defn process-api [{:keys [supervisor name]}]
  (str (supervisor-api supervisor) "/processes/" name))

(defn supervisor-title [{{:keys [host name]} :supervisor}]
  (str host "@" name))

(defn process-detail-url [{{:keys [host name]} :supervisor :as p}]
  (str "#/supervisors/" host "/" name "/" (:name p)))

(defn supervisor-detail-url [{{:keys [host name]} :supervisor :as p}]
  (str "#/supervisors/" host "/" name))

(defn process [{:keys [statename] :as p} owner]
  "Single process in a supervisor"
  (reify
    om/IRenderState
    (render-state [this {:keys [action-chan]}]
      (dom/li #js {:className (str statename " process pure-u-1")}
        (dom/span #js {:className "state pure-u"} statename)
        (dom/span #js {:className "controls pure-u"}
          (dom/i #js {:className "start fa fa-play"
                      :onClick #(put! action-chan [::start @p])})
          (dom/i #js {:className "stop fa fa-stop"
                      :onClick #(put! action-chan [::stop @p])})
          (dom/i #js {:className "stop fa fa-refresh"
                      :onClick #(put! action-chan [::restart @p])}))
        (dom/a #js {:className "name pure-u" :href (process-detail-url p)} (:name p))
        (dom/span #js {:className "pure-u"} " on ")
        (dom/a #js {:className "supervisor-name pure-u" :href (supervisor-detail-url p)} (supervisor-title p))
        (dom/span #js {:className "pure-u"} (:description p))))

    om/IInitState
    (init-state [this]
      {:action-chan (chan 1)
       :message-chan (chan)})

    om/IWillMount
    (will-mount [this]
      (let [action-chan (om/get-state owner :action-chan)
            message-chan (om/get-state owner :message-chan)
            process-api (process-api p)
            [start stop restart]
              (map #(str process-api "/action/" %) ["start", "stop" "restart"])]
        (go-loop [[k v] (<! action-chan)]
          (case k
            ::start   (handle-action! message-chan name "start"   start)
            ::stop    (handle-action! message-chan name "stop"    stop)
            ::restart (handle-action! message-chan name "restart" restart))
          (recur (<! action-chan)))))))

(defn prepare-processes [{supers :supervisors}]
  "Provide each process with select information about its supervisor"
  (mapcat
   (fn [s]
     (if (get-in s [:processes :fault-string])
       [] ;; dont try to render from unreachable supervisors
       (map #(assoc % :supervisor s) (:processes s))))
    supers))

(defn processes [state owner]
  "Collection of supervised processes"
  (om/component
   (let [processes (prepare-processes state)]
     (apply dom/ul #js {:className "processes"}
       (for [{:keys [supervisor name] :as p} processes]
         (om/build process p
           {:init-state (om/get-state owner)
            :react-key (str (:host supervisor) (:name supervisor) name)}))))))

(defn get-process [{{host :host sname :name pname :process} :route-params supers :supervisors}]
  (if-let [s (some-key= {:host host :name sname} supers)]
    (if-let [p (some-key= {:name pname} (:processes s))]
      (assoc p :supervisor s)
      {:error (str "Could not find process named '" pname "'")})
    {:error (str "Could not find supervisor named '" sname "' on host " host)}))

(defn process-log-url [process]
  (let [api-url (process-api process)
        host js/window.location.host]
    (str "ws://" host api-url "/log")))

(defn process-log-out-url [process]
  (str (process-log-url process) "/out"))

(defn process-log-err-url [process]
  (str (process-log-url process) "/err"))

(defn log-stream [api app-state owner]
  (reify
    om/IRenderState
    (render-state [this {lines :loglines}]
      (apply dom/pre #js {:className "pure-u-1 loglines"}
        (for [line lines]
          (dom/div nil line))))

    om/IInitState
    (init-state [this]
      {:control-chan (chan)
       :loglines []})

    om/IWillUnmount
    (will-unmount [this]
      (close! (om/get-state owner :control-chan)))

    om/IWillMount
    (will-mount [this]
      (let [control-ch (om/get-state owner :control-chan)]
        (go (let [ws (<! (ws-ch api))]
          (loop [[{:keys [message error]} ch] (alts! [ws control-ch])]
            (if-not (= ch ws)
              (close! ws)
              (when message
                (om/update-state! owner :loglines #(conj % message))
                (recur (alts! [ws control-ch])))))))))))

(defn log-err-stream [app-state owner]
  (log-stream (process-log-err-url app-state) app-state owner))

(defn log-out-stream [app-state owner]
  (log-stream (process-log-out-url app-state) app-state owner))

(defn process-detail [app-state owner]
  (reify
    om/IRenderState
    (render-state [this {:keys [active] :as state}]
      (let [p (get-process app-state)
            swap-state! (fn [e] (om/update-state! owner :active (fn [active] (case active :out :err :err :out))))]
        (if-not (:error p)
          (case active
            :out
            (dom/div #js {:className "pure-u-1 process-detail"}
              (om/build process p {:init-state state})
              (dom/button #js {:className "pure-button pure-button-active"} "stdout")
              (dom/button #js {:className "pure-button" :onClick swap-state!} "stderr")
              (om/build log-out-stream p {:init-state state}))
            :err
            (dom/div #js {:className "pure-u-1 process-detail"}
              (om/build process p {:init-state state})
              (dom/button #js {:className "pure-button" :onClick swap-state!} "stdout")
              (dom/button #js {:className "pure-button pure-button-active"} "stderr")
              (om/build log-err-stream p {:init-state state})))
          (dom/div nil))))

    om/IInitState
    (init-state [this]
      {:active :out})))
