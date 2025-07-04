(ns sift
  (:require [reagent.core :as r]
            [cljs.string :as str]
            ["js-yaml" :as yaml]))

;;; ============================================================================
;;; State Management & Event Dispatch
;;; ============================================================================

(defonce app-state (r/atom {:collections []
                             :filter-text ""}))

(defmulti handle-event (fn [event-type _] event-type))

(defmethod handle-event :default [event-type _]
  (js/console.error (str "Unhandled event type: " event-type)))

(defmethod handle-event :update-filter-text [_ [_ new-text]]
  (swap! app-state assoc :filter-text new-text))

(defmethod handle-event :add-collection [_ [_ collection]]
  (swap! app-state update :collections conj collection))

(defn dispatch [event-type & args]
  (handle-event event-type (cons event-type args)))

;;; ============================================================================
;;; Data Parsing & File Handling
;;; ============================================================================

(defn- parse-note-from-file [file-name content]
  (try
    (if (str/starts-with? content "+++")
      (let [[_ frontmatter body] (str/split content #"\+\+\+" 3)
            metadata (js->clj (.load yaml frontmatter) :keywordize-keys true)]
        {:id (random-uuid)
         :title (or (:title metadata) file-name)
         :tags (into #{"local"} (:tags metadata))
         :content (str/trim body)})
      ;; Fallback for files without frontmatter
      {:id (random-uuid) :title file-name :tags #{"local"} :content content})
    (catch js/Error e
      (js/console.error (str "Failed to parse " file-name ": " e))
      nil)))

(defn- process-file-entry [entry]
  (.file entry
         (fn [file]
           (.text file
                  (fn [text]
                    (when-let [note (parse-note-from-file (.name file) text)]
                      (dispatch :add-collection {:id (random-uuid)
                                                 :name (.name file)
                                                 :type :local-file
                                                 :notes [note]})))))))

(defn- process-directory-entry [entry]
  (let [reader (.createReader entry)]
    (js/Promise.
     (fn [resolve]
       (.readEntries reader
         (fn read-all [entries]
           (if (empty? entries)
             (resolve [])
             (let [file-entries (filter #(.isFile %) entries)
                   promises (map (fn [file-entry]
                                   (js/Promise.
                                    (fn [res]
                                      (.file file-entry
                                             (fn [file]
                                               (.text file
                                                      (fn [text]
                                                        (res (parse-note-from-file (.name file) text)))))))))
                                 file-entries)]
               (js/Promise.all promises resolve))))))
     (fn [parsed-notes]
       (let [valid-notes (remove nil? parsed-notes)]
         (when (seq valid-notes)
           (dispatch :add-collection {:id (random-uuid)
                                      :name (.name entry)
                                      :type :local-folder
                                      :notes valid-notes})))))))

(defn- handle-drop [e]
  (let [items (.. e -dataTransfer -items)]
    (doseq [item items]
      (when-let [entry (.webkitGetAsEntry item)]
        (if (.isDirectory entry)
          (process-directory-entry entry)
          (process-file-entry entry))))))

;;; ============================================================================
;;; Derived Views (Reactions)
;;; ============================================================================

(def filtered-collections
  (r/reaction
   (let [search-str (str/lower-case (:filter-text @app-state))]
     (if (str/blank? search-str)
       (:collections @app-state)
       (->> (:collections @app-state)
            (map (fn [collection]
                   (let [matching-notes
                         (filter
                          (fn [note]
                            (or (str/includes? (str/lower-case (:title note)) search-str)
                                (str/includes? (str/lower-case (:content note)) search-str)))
                          (:notes collection))]
                     (when (seq matching-notes)
                       (assoc collection :notes matching-notes)))))
            (remove nil?))))))

;;; ============================================================================
;;; UI Components
;;; ============================================================================

(defn drop-well-component []
  (let [drag-over? (r/atom false)]
    (fn []
      [:div.drop-well
       {:class (when @drag-over? "drag-over")
        :on-drag-over (fn [e] (.preventDefault e) (reset! drag-over? true))
        :on-drag-leave (fn [e] (.preventDefault e) (reset! drag-over? false))
        :on-drop (fn [e]
                   (.preventDefault e)
                   (reset! drag-over? false)
                   (handle-drop e))}
       "Drop files or a directory here"])))

(defn note-card [{:keys [title content tags]}]
  [:div.note-card
   [:h3 title]
   [:p (subs content 0 100) "..."]
   [:div.tags
    (for [tag tags] ^{:key tag} [:span.tag tag])]])

(defn collection-view [{:keys [name type notes]}]
  [:div.collection-view
   [:h2.collection-title (str name " (" (clojure.core/name type) ")")]
   [:div.notes-grid
    (for [note notes] ^{:key (:id note)} [note-card note])]])

(defn sifting-ui []
  [:div.sifting-ui
   [:h1 "Sifting UI"]
   [drop-well-component]
   [:input.filter-input
    {:type "text"
     :placeholder "Sift through notes..."
     :value (:filter-text @app-state)
     :on-change #(dispatch :update-filter-text (-> % .-target .-value))}]
   [:div.collections-container
    (for [collection @filtered-collections]
      ^{:key (:id collection)} [collection-view collection])]])

;;; ============================================================================
;;; Application Entry Point
;;; ============================================================================

(defn mount []
  (when-let [mount-el (.getElementById js/document "cljs-sift-app")]
    (r/render [sifting-ui] mount-el)))