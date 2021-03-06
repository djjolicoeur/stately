(defproject stately "0.1.2-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.clojure/tools.nrepl "0.2.10"]
                 [org.clojure/tools.namespace "0.2.3"]
                 [com.stuartsierra/component "0.2.3"]
                 [aysylu/loom "0.5.4"]]
  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[ch.qos.logback/logback-classic "1.1.2"
                                   :exclusions [org.slf4j/slf4j-api]]
                                  [org.slf4j/jul-to-slf4j "1.7.7"]
                                  [org.slf4j/jcl-over-slf4j "1.7.7"]
                                  [org.slf4j/log4j-over-slf4j "1.7.7"]
                                  [com.datomic/datomic-free "0.9.5344"
                                   :exclusions [joda-time org.slf4j/slf4j-nop]]]}})
