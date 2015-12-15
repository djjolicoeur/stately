(ns user
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.namespace.repl :refer (refresh)]
            [stately.components.executor :as exec]
            [stately.components.data-store :as data-store]
            [stately.components.state-store :as  state-store]
            [stately.graph.node :as node]
            [stately.graph.directed-graph :as dag]
            [stately.machine.state-machine :as sm]
            [stately.core :as stately]
            [mock-system :as msystem])
  (:import java.util.concurrent.Executors))


;; System Stuff
(def system)

(defn init []
  (alter-var-root #'system (constantly (msystem/dev-system))))

(defn start []
  (alter-var-root #'system component/start))

(defn stop []
  (alter-var-root #'system (fn [s]
                             (when s (component/stop s)))))

(defn go []
  (alter-var-root #'*out* (constantly *out*))
  (init)
  (start))

(defn reset []
  (stop)
  (refresh :after 'user/go))



;; Assuming you have started the system via '(go)', you can run the following
;; any of the following to see the school-application stately machine run.
;;

(defn run-dans-application
  "Dan should get accepted"
  []
  (let [appl (stately/stately (:stately-component system) msystem/dan)]
    (stately/input appl {})))

(defn run-caseys-application
  "Casey should get accepted, too....she's smarter than me"
  []
  (let [appl (stately/stately (:stately-component system) msystem/casey)]
    (stately/input appl {})))

(defn run-ginnys-application
  "Ginny won't get accepted.  that's ok, though.  Ginnys my dog."
  []
  (let [appl (stately/stately (:stately-component system) msystem/ginny)]
    (stately/input appl {})))

(defn run-everyone []
  (run-dans-application)
  (run-caseys-application)
  (run-ginnys-application)
  (println "You should see results in ~" (/  college-application/wait-time 1000)
           " seconds"))

(defn examine-data-store []
  (clojure.pprint/pprint
   @(data-store/get-data (stately/data-store (:stately-component system)))))

(defn examine-state-store []
  (clojure.pprint/pprint
   @(:state-atom (stately/state-store (:stately-component system)))))
