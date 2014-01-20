(defproject warden "0.0.1-SNAPSHOT"
  :description "a web app for supervisor"
  :url "https://github.com/eggsby/warden"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/core.async "0.1.267.0-0d7780-alpha"]
                 [compojure "1.1.6"]
                 [clj-yaml "0.4.0"]
                 [necessary-evil "2.0.0"]
                 [rm-hull/ring-gzip-middleware "0.1.7"]
                 [org.clojure/clojurescript "0.0-2138"]
                 [tailrecursion/cljson "1.0.6"]
                 [om "0.1.7"]
                 [sablono "0.2.1"]
                 [cljs-http "0.1.2"]]
  :plugins [[lein-ring "0.8.10"]
            [lein-aggravate "0.1.0-SNAPSHOT"]
            [lein-cljsbuild "1.0.1"]
            [lein-midje "3.1.3"]]
  :ring {:handler warden.handler/app}
  :source-paths ["src/clj"]
  :test-paths ["test/clj"]
  :cljsbuild {
    :builds [{:id "dev"
              :source-paths ["src/cljs"]
              :test-paths ["test/cljs"]
              :compiler {
                :output-to "resources/dev/public/js/core.js"
                :output-dir "resources/dev/public/js/external"
                :optimizations :none
                :source-map true}}
             {:id "release"
              :source-paths ["src/cljs"]
              :compiler {
                :output-to "resources/release/public/js/core.js"
                :optimizations :advanced
                :pretty-print false
                :preamble ["react/react.min.js"]
                :externs ["react/externs/react.js"]}}]}
  :profiles
  {:release {:aggravate-files [{:input ["resources/dev/public/css/pure.css"
                                        "resources/dev/public/css/font-awesome.css"
                                        "resources/dev/public/css/fonts.css"
                                        "resources/dev/public/css/warden.css"]
                                :output "resources/release/public/css/warden.css"
                                :compressor "yui"}]
             :resource-paths ["resources/release"]}
   :dev {:resource-paths ["resources/dev"]
         :dependencies [[javax.servlet/servlet-api "2.5"]
                        [midje "1.6.0"]
                        [ring-mock "0.1.5"]]}})
