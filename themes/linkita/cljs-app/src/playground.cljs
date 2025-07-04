(ns playground
  (:require [reagent.core :as r]
            ["cytoscape" :as cytoscape]
            ["cytoscape-edgehandles" :as edgehandles]))

(cytoscape.use edgehandles)

;;; ============================================================================
;;; Configuration & Data
;;; ============================================================================

(def exercises
  [{:id :connect-nodes
    :prompt "Connect the 'Web Client' to the 'API Server' to represent a request."
    :initial-elements
    [{:data {:id "client" :label "Web Client" :compute 1}}
     {:data {:id "api" :label "API Server" :compute 1}}]
    :validation (fn [cy] (if (> (-> cy (.edges "[source='client'][target='api']") .js_length) 0)
                          {:valid? true :message "Correct!"}
                          {:valid? false :message "Not quite. Try creating an edge."})))}])

(def node-types
  [{:type :api-server :label "API Server" :style {:background-color "#0d9488"}}
   {:type :database :label "Database" :style {:background-color "#9f1239"}}
   {:type :edge-worker :label "Edge Worker" :style {:background-color "#ca8a04"}}
   {:type :web-client :label "Web Client" :style {:background-color "#4f46e5"}}])

(def graph-style
  [{:selector "node" :style {:background-color "#475569" :label "data(label)" :color "#f1f5f9" :text-outline-color "#475569" :text-outline-width 2 :transition-property "background-color, border-color, width, height" :transition-duration "0.2s" :width "mapData(compute, 1, 16, 60, 120)" :height "mapData(compute, 1, 16, 60, 120)"}}
   {:selector "edge" :style {:width 2 :line-color "#94a3b8" :target-arrow-color "#94a3b8" :target-arrow-shape "triangle" :curve-style "bezier"}}
   {:selector ":selected" :style {:background-color "#2563eb" :border-color "#60a5fa" :border-width 3}}
   {:selector ".hover" :style {:background-color "#64748b"}}])

(def exercise-layout {:name "grid" :rows 1 :padding 50})
(def playground-layout {:name "grid" :padding 50})

;;; ============================================================================
;;; Toolbelt Definition
;;; ============================================================================

(def tools
  [{:id :scale-up
    :label "Scale Up"
    :prompt "Increase compute resources. This makes the node more powerful but also more expensive."
    :svg-icon [:svg {:viewBox "0 0 20 20" :fill "currentColor"} [:path {:d "M4.5 2.5a2.5 2.5 0 100 5 2.5 2.5 0 000-5zM8.5 5a2.5 2.5 0 11-5 0 2.5 2.5 0 015 0zM11.5 2.5a2.5 2.5 0 100 5 2.5 2.5 0 000-5zM15.5 5a2.5 2.5 0 11-5 0 2.5 2.5 0 015 0zM4.5 12.5a2.5 2.5 0 100 5 2.5 2.5 0 000-5zM8.5 15a2.5 2.5 0 11-5 0 2.5 2.5 0 015 0zM11.5 12.5a2.5 2.5 0 100 5 2.5 2.5 0 000-5zM15.5 15a2.5 2.5 0 11-5 0 2.5 2.5 0 015 0z"}]]
    :effect (fn [cy node]
              (let [new-compute (min 16 (* (or (.data node "compute") 1) 2))]
                (.data node "compute" new-compute)))}
   {:id :add-redundancy
    :label "Add Redundancy"
    :prompt "Create a replica to handle failover and increase availability."
    :svg-icon [:svg {:viewBox "0 0 20 20" :fill "currentColor"} [:path {:d "M7 3a1 1 0 000 2h6a1 1 0 100-2H7zM4 7a1 1 0 011-1h10a1 1 0 110 2H5a1 1 0 01-1-1zM2 11a1 1 0 011-1h14a1 1 0 110 2H3a1 1 0 01-1-1z"}]]
    :effect (fn [cy node]
              (let [new-id (str (.id node) "-replica-" (rand-int 100))
                    pos (.position node)]
                (.add cy #js{:group "nodes"
                             :data {:id new-id :label (str (.data node "label") " (Replica)") :compute (.data node "compute")}
                             :position {:x (+ (:x pos) 50) :y (+ (:y pos) 50)}})
                (.add cy #js{:group "edges" :data {:source (.id node) :target new-id}})))}
   {:id :inspect
    :label "Inspect"
    :prompt "View the internal data and properties of this component."
    :svg-icon [:svg {:viewBox "0 0 20 20" :fill "currentColor"} [:path {:fill-rule "evenodd" :d "M9 4.75A.75.75 0 019.75 4h.5a.75.75 0 010 1.5h-.5A.75.75 0 019 4.75zm0 10.5a.75.75 0 01.75-.75h.5a.75.75 0 010 1.5h-.5a.75.75 0 01-.75-.75zM9.75 7a.75.75 0 000 1.5h.5a.75.75 0 000-1.5h-.5zm.75 2.25a.75.75 0 01.75-.75h.5a.75.75 0 010 1.5h-.5a.75.75 0 01-.75-.75z" :clip-rule "evenodd"}]]
    :effect (fn [cy node] (js/console.log "Inspecting Node:" (js/JSON.stringify (.json node) nil 2)))}])

;;; ============================================================================
;;; State Management
;;; ============================================================================

(defonce app-state (r/atom {:mode :exercise
                             :current-exercise-idx 0
                             :validation-result nil
                             :cytoscape-instance nil
                             :selected-node nil}))

;;; ============================================================================
;;; UI Components
;;; ============================================================================

(defn toolbelt []
  [:div.toolbelt
   [:h4 "Toolbelt"]
   (for [{:keys [id label prompt svg-icon effect]} tools]
     ^{:key id}
     [:div.tool {:title prompt
                 :on-click (fn [] (when-let [cy @(:cytoscape-instance @app-state)]
                                   (effect cy (:selected-node @app-state))))}
      [:div.icon svg-icon]
      [:span.label label]])])

(defn node-palette []
  [:div.node-palette
   [:h4 "Components"]
   (for [{:keys [type label style]} node-types]
     ^{:key type}
     [:button {:on-click (fn []
                           (when-let [cy @(:cytoscape-instance @app-state)]
                             (.add cy #js{:group "nodes"
                                          :data {:id (str (name type) "-" (rand-int 1000)) :label label :compute 1}
                                          :style style})))}
      label])])

(defn control-panel []
  (let [cy @(:cytoscape-instance @app-state)
        mode (:mode @app-state)
        current-exercise (get exercises (:current-exercise-idx @app-state))]
    [:div.control-panel
     [:h3 (if (= mode :exercise) "Exercise" "Playground")]
     [:p.prompt (if (= mode :exercise) (:prompt current-exercise) "Build your own architecture.")]
     (when (= mode :exercise)
       [:button.check-work
        {:on-click (fn []
                     (let [result ((:validation current-exercise) cy)]
                       (swap! app-state assoc :validation-result result)
                       (when (:valid? result)
                         (js/setTimeout
                          #(do
                             (swap! app-state update :current-exercise-idx (fn [i] (min (inc i) (dec (count exercises)))))
                             (swap! app-state assoc :validation-result nil))
                          2000))))}
        "Check My Work"])
     (when-let [result (:validation-result @app-state)]
       [:p.feedback {:class (if (:valid? result) "valid" "invalid")} (:message result)])
     
     (if (:selected-node @app-state)
       [toolbelt]
       [:div.shared-controls
        [:button {:on-click (fn [] (when cy (-> cy .elements .remove)))} "Clear Canvas"]
        [node-palette]])]))

(defn mode-toggle []
  (let [current-mode (:mode @app-state)]
    [:div.mode-toggle
     [:button {:class (when (= current-mode :exercise) "active") :on-click #(swap! app-state assoc :mode :exercise)} "Exercise Mode"]
     [:button {:class (when (= current-mode :playground) "active") :on-click #(swap! app-state assoc :mode :playground)} "Playground Mode"]]))

(defn playground-component []
  (let [cy-container (r/atom nil)]
    (r/create-class
     {:component-did-mount
      (fn [this]
        (try
          (let [cy (cytoscape #js{:container @cy-container :style (clj->js graph-style)})
                eh (.edgehandles cy #js{:snap true :handleNodes "node" :handleColor "#60a5fa" :handleSize 10})]
            (swap! app-state assoc :cytoscape-instance cy)
            (.on cy "select" "node" (fn [e] (swap! app-state assoc :selected-node (.-target e))))
            (.on cy "unselect" "node" (fn [_] (swap! app-state assoc :selected-node nil)))
            (.on cy "mouseover" "node" (fn [e] (-> e .-target .addClass "hover")))
            (.on cy "mouseout" "node" (fn [e] (-> e .-target .removeClass "hover"))))
          (catch js/Error e (js/console.error "Cytoscape initialization failed:" e))))
      :component-will-unmount
      (fn [this]
        (when-let [cy @(:cytoscape-instance @app-state)] (.destroy cy))
        (reset! app-state {:mode :exercise :current-exercise-idx 0 :validation-result nil :cytoscape-instance nil :selected-node nil}))
      :component-did-update
      (fn [this [_ old-props]]
        (let [{:keys [mode cytoscape-instance current-exercise-idx]} @app-state
              old-mode (get old-props :mode)
              old-idx (get old-props :current-exercise-idx)]
          (when cytoscape-instance
            (when (not= mode old-mode)
              (-> cytoscape-instance .elements .remove)
              (if (= mode :exercise)
                (let [exercise (get exercises current-exercise-idx)]
                  (.add cytoscape-instance (clj->js (:initial-elements exercise)))
                  (-> cytoscape-instance .layout (clj->js exercise-layout) .run))
                (-> cytoscape-instance .layout (clj->js playground-layout) .run)))
            (when (and (= mode :exercise) (not= current-exercise-idx old-idx))
              (let [exercise (get exercises current-exercise-idx)]
                (-> cytoscape-instance .elements .remove)
                (.add cytoscape-instance (clj->js (:initial-elements exercise)))
                (-> cytoscape-instance .layout (clj->js exercise-layout) .run))))))
      :reagent-render
      (fn [props]
        [:div {:style {:width "100%" :height "100%" :display "flex" :flex-direction "column"}}
         [mode-toggle]
         [:div {:style {:flex-grow 1 :display "flex"}}
          [:div.graph-container {:style {:flex-grow 1} :ref #(reset! cy-container %)}]
          [control-panel]]])})))

(defn mount []
  (when-let [mount-el (.getElementById js/document "cljs-playground-app")]
    (r/render [playground-component] mount-el)))