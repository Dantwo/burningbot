(defproject burningbot "0.2.0-SNAPSHOT"
  :description "An IRC bot for #BurningWheel"
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [irclj "0.4.1-SNAPSHOT"]
                 [enlive "1.0.0"]
                 [clj-time "0.3.0"]
		 [delimc "0.1.0"]
                 [ring/ring-core "0.3.8"]
                 [ring/ring-jetty-adapter "0.3.8"]
                 [net.cgrand/moustache "1.0.0"]
                 [necessary-evil "1.1.0"]
                 [mysql/mysql-connector-java "5.1.13"]
                 [clojureql "1.1.0-SNAPSHOT"]]
  :dev-dependencies [[org.xerial/sqlite-jdbc "3.7.2"]]
  :resources-path "resources"
  :aot [burningbot.run]
  :main burningbot.run)
