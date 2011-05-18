(ns burningbot.web.utils
  "A grab bag of utilities for ring"
  (:use [ring.util.response :only [response content-type]]
        [clojure.contrib.json :only [json-str]]))


(defn request-is-ajax?
  [req]
  (= (get-in req [:headers "x-requested-with"])
     "XMLHttpRequest"))

(def html-response (comp #(content-type % "text/html; charset=utf8")
                         response))

(def json-response (comp #(content-type % "application/json; charset=utf8")
                         response
                         json-str))
