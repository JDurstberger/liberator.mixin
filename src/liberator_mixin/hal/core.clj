(ns liberator-mixin.hal.core
  "A liberator mixin to add
  [HAL](https://tools.ietf.org/html/draft-kelly-json-hal-00) support to
  liberator.

  In short:

    - Adds `application/hal+json` as a supported media type.
    - Adds support for JSON serialisation for maps and seqs for the HAL
      media type.
    - Adds support for [halboy](https://github.com/jimmythompson/halboy)
      resource serialisation.
    - Adds a default handler responding with an empty HAL resource for
      `:handle-not-found`.
    - Adds a HAL error representation for use with `liberator-mixin.validation`.

  Depends on:

    - `liberator-mixin.json`,
    - `liberator-mixin.hypermedia`.

  Optionally extends:

    - `liberator-mixin.validation`.

  ### JSON serialisation support



  ### halboy `Resource` support

  The [halboy](https://github.com/jimmythompson/halboy) resource support
  will add a `:discovery` link to any returned resource and expects `bidi`
  `:routes` to be available in the `context`, containing a route named
  `:discovery`."
  (:require
    [halboy.resource :as hal]
    [halboy.json :as haljson]

    [hype.core :as hype]

    [jason.convenience :as jason-conv]

    [liberator.representation :as r]
    [liberator-mixin.logging.core :as log])
  (:import
    [halboy.resource Resource]
    [java.util UUID]))

(defn- random-uuid []
  (str (UUID/randomUUID)))

(def hal-media-type
  "The HAL media type string."
  "application/hal+json")

(extend-protocol r/Representation
  Resource
  (as-response [data {:keys [request routes] :as context}]
    (r/as-response
      (-> data
        (hal/add-link :discovery
          (hype/absolute-url-for request routes :discovery))
        (haljson/resource->map))
      context)))

(defmethod r/render-map-generic hal-media-type [data _]
  (jason-conv/->wire-json data))

(defmethod r/render-seq-generic hal-media-type [data _]
  (jason-conv/->wire-json data))

(defn with-hal-media-type
  "Returns a mixin to add the HAL media type to the available media types."
  []
  {:available-media-types
   [hal-media-type]

   :service-available?
   {:representation {:media-type hal-media-type}}})

(defn with-hal-error-representation
  "Returns a mixin adding a HAL error representation factory function to the
  resource, at `:error-representation`, for use by other mixins, such as
  `liberator-mixin.validation` when they need to render errors.

  The error representation factory function expects the context to include
  a `:self` href, an `:error-id` and an `:error-context` used in the resulting
  representation."
  []
  {:error-representation
   (fn [{:keys [self error-id error-context]}]
     (->
       (hal/new-resource self)
       (hal/add-property :error-id error-id)
       (hal/add-property :error-context error-context)))})

(defn with-exception-handler
  "Returns a mixin which adds a generic exception handler, logging the
  exception and returning an error representation masking the exception.

  This mixin expects a `:logger` to be present on the resource. If no `:logger`
  is found, nothing will be logged."
  []
  {:handle-exception
   (fn [{:keys [exception resource]}]
     (let [error-id (random-uuid)
           message "Request caused an exception"]
       (when-let [get-logger (:logger resource)]
         (log/log-error (get-logger) message
           {:error-id error-id}
           exception))
       (hal/add-properties
         (hal/new-resource)
         {:error-id error-id
          :message message})))})

(defn with-not-found-handler []
  {:handle-not-found
   (fn [{:keys [not-found-message]
         :or   {not-found-message "Resource not found"}}]
     (hal/add-properties
       (hal/new-resource)
       {:error not-found-message}))})

(defn with-hal-mixin [_]
  [(with-hal-media-type)
   (with-hal-error-representation)
   (with-not-found-handler)])
