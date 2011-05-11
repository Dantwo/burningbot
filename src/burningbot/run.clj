(ns burningbot.run
  (:use [burningbot.core])
  (:gen-class))

(defn -main [& args]
  (start-web (start-bot)))
