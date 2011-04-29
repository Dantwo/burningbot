(defproject burningbot "1.0.0-SNAPSHOT"
  :description "FIXME: write description"
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [irclj "0.4.1-SNAPSHOT"]
                 [enlive "1.0.0"]
                 [clj-time "0.3.0"]]
  :resources-path "resources"
  :aot [burningbot.run]
  :main burningbot.run)
