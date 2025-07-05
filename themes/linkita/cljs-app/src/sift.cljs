(ns sift
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            [clojure.string :as str]
            ["js-yaml" :as yaml]))

;; ... (rest of the file is the same until the mount function)

(defn mount []
  (when-let [mount-el (.getElementById js/document "cljs-sift-app")]
    (rdom/render [sifting-ui] mount-el)))