(ns warden.net
  (:require [cljs-http.client :as http]
            [tailrecursion.cljson :refer [cljson->clj]]
            [cljs.core.async :refer [>! <! timeout]])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

(defn cljson-request [req]
  "Decorates a request with cljson headers"
  (assoc-in req [:headers "Accept"] "application/cljson"))

(defn cljson-get [url & [req]]
  "HTTP GET with cljson headers"
  (try (http/get url (cljson-request req))
    (catch js/Error e (js/console.log "err"))))

(defn cljson-post [url & [req]]
  "HTTP POST with cljson headers"
  (try (http/post url (cljson-request req))
    (catch js/Error e (js/console.log "err"))))

(defn parse [{body :body}]
  (vec (cljson->clj body)))

(defn poll! [url wait ch]
  "Poll a url every `wait` ms, delivering responses on ch"
  ;; TODO: handle errors from http/get
  (go-loop [response (<! (cljson-get url))]
    (when response
      (>! ch response))
    (<! (timeout wait))
    (recur (<! (cljson-get url)))))
