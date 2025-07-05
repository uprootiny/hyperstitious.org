(ns main
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]))

(defn probe-component []
  ;; This component remains unchanged for now.
  (let [show-questions? (r/atom false)]
    (fn []
      [:div.probe-container
       [:button.probe-button
        {:on-click #(swap! show-questions? not)}
        (if @show-questions? "Hide Probes" "Probe this Idea")]
       (when @show-questions?
         [:div.probe-questions
          [:h4 "Generative Questions"]
          [:ul
           (for [q ["What are the second-order effects of this?"
                    "How could this be scaled up or down?"
                    "What is the most charitable interpretation of this idea?"
                    "What is the most cynical one?"
                    "Who benefits if this succeeds? Who is disadvantaged?"]]
             ^{:key q} [:li q])]])])))


(defn ^:export init []
  ;; Page-specific component router
  (cond
    ;; If we find probe mounts, mount the probe component
    (> (.. js/document (querySelectorAll ".cljs-probe-mount") -length) 0)
    (let [mount-elements (.querySelectorAll js/document ".cljs-probe-mount")]
      (doseq [el mount-elements]
        (rdom/render [probe-component] el)))

    ;; If we find the sift app mount, load the sift module
    (.getElementById js/document "cljs-sift-app")
    (js/require #js ["./sift.js"] (fn [sift] (sift/mount)))

    ;; If we find the playground app mount, load the playground module
    (.getElementById js/document "cljs-playground-app")
    (js/require #js ["./playground.js"] (fn [pg] (pg/mount)))))
