(defproject warden "0.0.1-SNAPSHOT"
  :description "a web app for supervisor"
  :url "https://github.com/eggsby/warden"

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2173"]
                 [org.clojure/core.async "0.1.278.0-76b25b-alpha"]
                 [tailrecursion/cljson "1.0.6"]
                 [clj-yaml "0.4.0"]
                 [com.cemerick/friend "0.2.0"]
                 [http-kit "2.1.16"]
                 [liberator "0.10.0"]
                 [compojure "1.1.6"]
                 [jarohen/chord "0.3.1"]
                 [rm-hull/ring-gzip-middleware "0.1.7"]
                 [prismatic/schema "0.2.0"]
                 [necessary-evil "2.0.0"]
                 [om "0.5.3"]
                 [secretary "1.1.0"]
                 [cljs-http "0.1.5"]
                 [alandipert/storage-atom "1.1.2"]]

  :source-paths ["src/clj" "target/cljx-generated/clj"]
  :test-paths ["test/clj"]

  :main warden.core
  :aot :all

  :plugins [[lein-cljsbuild "1.0.1"]
            [com.keminglabs/cljx "0.3.2"]]

  :cljx {:builds [{:source-paths ["src/cljx"]
                   :output-path "target/cljx-generated/clj"
                   :rules :clj}
                  {:source-paths ["src/cljx"]
                   :output-path "target/cljx-generated/cljs"
                   :rules :cljs}]}

  :cljsbuild {
    :builds {
      :dev {:source-paths ["src/cljs" "target/cljx-generated/cljs"]
            :test-paths ["test/cljs"]
            :compiler {:output-to "resources/dev/public/js/core.js"
                       :output-dir "resources/dev/public/js/external"
                       :optimizations :none
                       :source-map true}}
      :karma {:source-paths ["src/cljs" "target/cljx-generated/cljs" "test/cljs"]
              :compiler {:output-to "target/cljs-build-karma/warden-test.js"
                         :output-dir "target/cljs-build-karma"
                         :optimizations :whitespace
                         :pretty-print true
                         :preamble ["react/react.min.js"]
                         :externs ["react/externs/react.js"]}}
      :release {:source-paths ["src/cljs" "target/cljx-generated/cljs"]
                :compiler {:output-to "resources/release/public/js/core.js"
                           :output-dir "target/cljs-build"
                           :optimizations :advanced
                           :pretty-print false
                           :preamble ["react/react.min.js"]
                           :externs ["react/externs/react.js"]}}}}

  :profiles {
    :release {:aggravate-files [{:input ["resources/dev/public/css/pure.css"
                                         "resources/dev/public/css/font-awesome.css"
                                         "resources/dev/public/css/fonts.css"
                                         "resources/dev/public/css/warden.css"]
                                 :output "resources/release/public/css/warden.css"
                                 :compressor "yui"}]
              :plugins [[lein-aggravate "0.1.0-SNAPSHOT"]]
              :resource-paths ["resources/release"]}
    :dev {:resource-paths ["resources/dev"]
          :plugins [[lein-midje "3.1.3"]]
          :dependencies [[javax.servlet/servlet-api "2.5"]
                         [midje "1.6.0"]
                         [im.chit/purnam "0.1.8"]
                         [ring-mock "0.1.5"]]}})
