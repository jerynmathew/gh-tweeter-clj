(defproject gh-tweeter-clj "1.0.0-SNAPSHOT"
  :description "FIXME: write description"
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/data.json "0.2.6" :scope "provided"]
                 [com.cemerick/url "0.1.1"]
                 [clj-http-lite "0.3.0"]]
  :main ^:skip-aot gh-tweeter-clj.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}
  )