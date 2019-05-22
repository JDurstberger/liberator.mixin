(ns liberator-mixin.hal.core
  (:require
    [halboy.resource :as hal]
    [halboy.json :as hal-json]

    [liberator.representation :as r]

    [liberator-mixin.core :as core]
    [liberator-mixin.util :as util]
    [liberator-mixin.hypermedia.core :as hypermedia]
    [liberator-mixin.json.core :as json]
    [liberator-mixin.logging.core :as log]))

(def hal-media-type "application/hal+json")

(extend-protocol r/Representation
  halboy.resource.Resource
  (as-response [data {:keys [request routes] :as context}]
    (r/as-response
      (-> data
        (hal/add-link
          :discovery
          {:href (hypermedia/absolute-url-for request routes :discovery)})
        (hal-json/resource->map))
      context)))

(defmethod r/render-map-generic hal-media-type [data _]
  (json/map->wire-json data))

(defn with-hal-media-type []
  {:available-media-types
   [hal-media-type]

   :service-available?
   {:representation {:media-type hal-media-type}}})

(defn with-exception-handler []
  {:handle-exception
   (fn [{:keys [exception resource]}]
     (let [error-id (util/random-uuid)
           message "Request caused an exception"]
       (do
         (when-let [get-logger (:logger resource)]
           (log/log-error
             (get-logger)
             message
             {:error-id error-id}
             exception))
         (hal/add-properties
           (hal/new-resource)
           {:error-id error-id
            :message  message}))))})

(defn with-not-found-handler []
  {:handle-not-found
   (fn [{:keys [not-found-message]
         :or   {not-found-message "Resource not found"}}]
     (hal/add-properties
       (hal/new-resource)
       {:error not-found-message}))})

(defn with-hal-mixin [_]
  (apply core/merge-resource-definitions
    [(with-hal-media-type)
     (with-not-found-handler)]))