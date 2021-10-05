(defproject  stigmergy/mercury "0.1.5-SNAPSHOT"
  :source-paths ["src"]
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/core.async "1.3.618"]]
  :deploy-repositories [#_["releases" :clojars]
                        ["snapshots" :clojars]])
