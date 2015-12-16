(defproject stately "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.clojure/tools.nrepl "0.2.10"]
                 [org.clojure/tools.namespace "0.2.3"]
                 [com.stuartsierra/component "0.2.3"]
                 [aysylu/loom "0.5.0"]]
  :profiles {:dev {:source-paths ["dev"]
                   :plugins [[cider/cider-nrepl "0.10.0-SNAPSHOT"]]}
             :test {:dependencies [[com.datomic/datomic-free "0.9.5344"]]}})
