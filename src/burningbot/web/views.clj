(ns burningbot.web.views
  (:use [net.cgrand.enlive-html :only [defsnippet deftemplate]])
  (:require [net.cgrand.enlive-html :as html]))

(def ^:private content-snippets (html/html-resource "templates/content-snippets.html"))

(def loading (first (html/select content-snippets [:article.loading])))

(def home (first (html/select content-snippets [:article.home])))

(def colophon (first (html/select content-snippets [:article.colophon])))

(deftemplate main-template "templates/main-template.html" [title content]
  [:head :title]                     (html/content title)
  [:div#container :section.log-body] (html/content content))
