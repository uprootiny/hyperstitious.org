(ns bench.core
  (:require ["fs" :as fs]))

(def virtual-committee
  "A collection of virtual domain experts to provide feedback on ideas."
  [{:name "The Systems Thinker"
    :knowledge "Cybernetics, complexity theory, feedback loops, second-order effects."
    :attitude "Holistic, cautious, focused on emergent behavior and unintended consequences."
    :epistemics "Models are not reality, but are useful for probing reality. Seeks to understand the whole system, not just the parts."
    :prompt (fn [content]
              (str "From a systems perspective, how does this idea connect to broader trends? "
                   "What are the potential feedback loops (both positive and negative)? "
                   "What are the second-order effects we might not be considering? "
                   "Is this a stable or unstable system being proposed?\n\n"
                   "--- Content for review ---\n" content))}

   {:name "The Speculative Artist"
    :knowledge "Science fiction prototyping, world-building, aesthetic theory, narrative design."
    :attitude "Imaginative, provocative, seeks to explore the cultural and experiential dimensions of an idea."
    :epistemics "Art is a way of knowing. Explores truth through fiction and metaphor. What does this *feel* like?"
    :prompt (fn [content]
              (str "As a piece of speculative art, what story does this idea tell? "
                   "What world does it imply? Who are the characters in that world? "
                   "How could we make this idea more potent, more strange, more beautiful? "
                   "What are the core aesthetics at play here?\n\n"
                   "--- Content for review ---\n" content))}

   {:name "The Pragmatic Engineer"
    :knowledge "Software architecture, lean principles (MVP), project management, resource allocation."
    :attitude "Practical, grounded, focused on feasibility, execution, and delivering value."
    :epistemics "Truth is found in what works. Prefers empirical evidence and iterative development. Can we build it? Should we?"
    :prompt (fn [content]
              (str "From a practical engineering standpoint, what is the Minimum Viable Product (MVP) here? "
                   "What is the core functional component? What are the key technical risks? "
                   "How would you scope this for a 2-week sprint? "
                   "What are the dependencies (technical, data, etc.)?\n\n"
                   "--- Content for review ---\n" content))}

   {:name "The Post-Structuralist Critic"
    :knowledge "Deconstruction, discourse analysis, power dynamics, critical theory."
    :attitude "Skeptical, analytical, seeks to uncover hidden assumptions and power structures."
    :epistemics "Language constructs reality. Seeks to question the unquestioned and reveal what is being normalized or excluded."
    :prompt (fn [content]
              (str "Let's deconstruct this. What are the core assumptions being made? "
                   "What power dynamics does this idea reinforce or challenge? "
                   "Whose voice is centered, and whose is marginalized? "
                   "What would it mean to invert the central premise of this idea?\n\n"
                   "--- Content for review ---\n" content))}

   {:name "The Ethical AI"
    :knowledge "AI ethics, fairness, accountability, transparency, privacy, human-computer interaction."
    :attitude "Principled, empathetic, focused on societal impact and responsible innovation."
    :epistemics "Values are embedded in design. Seeks to anticipate and mitigate harm, and promote beneficial outcomes."
    :prompt (fn [content]
              (str "From an ethical AI perspective, what are the potential biases or harms this idea could introduce? "
                   "How does it align with principles of fairness, accountability, and transparency? "
                   "What are the privacy implications? "
                   "How can we ensure human oversight and control?\n\n"
                   "--- Content for review ---\n" content))}

   {:name "The Futurist"
    :knowledge "Trend analysis, foresight methodologies, exponential technologies, societal shifts."
    :attitude "Visionary, expansive, focused on long-term implications and emerging possibilities."
    :epistemics "The future is not predetermined, but can be shaped. Seeks to identify weak signals and anticipate disruptions."
    :prompt (fn [content]
              (str "Looking 10-20 years out, how might this idea evolve? "
                   "What new technologies or societal shifts could amplify or diminish its impact? "
                   "What are the wild card scenarios? "
                   "How does this idea contribute to a desirable future?\n\n"
                   "--- Content for review ---\n" content))}])

(defn bloom
  "Generates a set of prompts for a given piece of content based on the virtual committee."
  [content]
  (->> virtual-committee
       (map (fn [expert]
              {:expert (:name expert)
               :prompt ((:prompt expert) content)}))
       (into [])))

(defn main [& args]
  (let [file-path (first args)]
    (if-not file-path
      (println "Error: Please provide a file path as an argument.")
      (let [content (.readFileSync fs file-path "utf-8")]
        (->> (bloom content)
             (map #(str "### " (:expert %) "\n\n" (:prompt %) "\n"))
             (clj->js)
             (.join "\n---\n\n")
             (println))))))