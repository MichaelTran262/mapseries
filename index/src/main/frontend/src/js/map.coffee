import mapboxgl from 'mapbox-gl'
import $ from 'jquery'
import bootbox from 'bootbox'
import loading from 'js/loading'
import turf from 'turf'
import page from 'js/page'
import 'js/autocomplete'

showError = (msg) ->
  bootbox.alert {
    size: 'large',
    title: window.msg.error,
    message: msg
  }

export default {
  main: ->
    # default zoom, center and rotation
    defaultZoom = 5
    defaultCenter = [20, 46]
    defaultBearing = 0
    defaultPitch = 0

    zoom = defaultZoom
    center = defaultCenter
    bearing = defaultBearing
    pitch = defaultPitch

    restoreMapPosition = ->
      hash = page.getAnchor('map', '')
      parts = hash.split('/')
      if parts.length == 5
        zoom = parseInt(parts[0], 10)
        center = [
          parseFloat(parts[1]),
          parseFloat(parts[2])
        ]
        bearing = parseFloat(parts[3])
        pitch = parseFloat(parts[4])
      else
        zoom = defaultZoom
        center = defaultCenter
        bearing = defaultBearing
        pitch = defaultPitch

    getAnchor = ->
      c = map.getCenter()
      z = Math.round(map.getZoom() * 10000) / 10000
      x = Math.round(c.lng * 100) / 100
      y = Math.round(c.lat * 100) / 100
      b = Math.round(map.getBearing() * 100) / 100
      p = Math.round(map.getPitch() * 100) / 100
      "#{z}/#{x}/#{y}/#{b}/#{p}"

    restoreMapPosition()

    map = new mapboxgl.Map
        container: 'map'
        style:
            "version": 8
            "sources":
                "base-map-source":
                    "type": "raster"
                    "tiles": [
                        "https://a.tile.openstreetmap.org/{z}/{x}/{y}.png"
                        "https://b.tile.openstreetmap.org/{z}/{x}/{y}.png"
                    ],
                    "tileSize": 256
            "layers": [{
                "id": "base-map-layer"
                "type": "raster"
                "source": "base-map-source"
                "minzoom": 0
                "maxzoom": 22
            }]
            "glyphs": "https://fonts.openmaptiles.org/{fontstack}/{range}.pbf"
        center: center
        zoom: zoom
        bearing: bearing
        pitch: pitch

    map.addControl(new mapboxgl.NavigationControl())

    loadGrid = ->
      loading.show()
      $.ajax {
        type: 'GET'
        url: "#{window.contextPath}/server/data/geojson/#{window.mapSettings.grid}.json"
        dataType: "json",
        success: (geojson) ->
          loading.hide()

          labels = []
          posFeatures = []
          negFeatures = []

          geojson.features.forEach (feature) ->
            sheetId = feature.properties.SHEET.toString()
            if sheetId in window.mapSettings.sheetIds
              posFeatures.push(feature)
            else
              negFeatures.push(feature)

          posFeatures.forEach (feature) ->
            centroid = turf.centroid(feature)
            centroid.properties = feature.properties
            labels.push(centroid)

          map.addSource("geojson-pos-source", {
            "type": "geojson"
            "data": {
              "type": "FeatureCollection",
              "features": posFeatures
            }
          })

          map.addSource("geojson-neg-source", {
            "type": "geojson"
            "data": {
              "type": "FeatureCollection",
              "features": negFeatures
            }
          })

          map.addLayer({
            "id": "geojson-pos-layer"
            "type": "fill"
            "source": "geojson-pos-source"
            "paint": {
                "fill-color": "#888"
                "fill-outline-color": "#000"
                "fill-opacity": 0.8
            }
          })

          map.addLayer({
            "id": "geojson-neg-layer"
            "type": "fill"
            "source": "geojson-neg-source"
            "paint": {
                "fill-color": "#888"
                "fill-outline-color": "#000"
                "fill-opacity": 0.2
            }
          })

          map.addSource("label-source", {
            "type": "geojson",
            "data": {
              "type": "FeatureCollection",
              "features": labels
            }
          });
          map.addLayer({
            "id": "label-layer",
            "type": "symbol",
            "source": "label-source",
            "layout": {
              "text-field": "{SHEET}",
              "text-font": ["Open Sans Light"],
            }
          });

          map.on 'click', 'label-layer', (e) ->
            id = e.features[0].properties['SHEET']
            location.href = "#{window.contextPath}/sheet.xhtml?serie=#{encodeURIComponent(window.mapSettings.serie)}&sheet=#{id}#map=#{getAnchor()}"

          map.on 'mouseenter', 'label-layer', () -> map.getCanvas().style.cursor = 'pointer'
          map.on 'mouseleave', 'label-layer', () -> map.getCanvas().style.cursor = ''

        error: (err) ->
          loading.hide()
          showError "Server returned code #{err.status} with message: #{err.responseText}"
      }

    if window.mapSettings
      loadGrid()

    updatingMapState = false
    updatePermalink = (e, onlyLinks) ->
      if updatingMapState
        # do not update the URL when the view was changed in the 'popstate' handler
        return

      updatingMapState = true

      if onlyLinks
        page.updateAnchorOnlyLinks("map", getAnchor())
      else
        page.updateAnchor("map", getAnchor())

      updatingMapState = false

    map.on('moveend', updatePermalink)
    $(() -> updatePermalink(null, true))

    # restore the view state when navigating through the history, see
    # https://developer.mozilla.org/en-US/docs/Web/API/WindowEventHandlers/onpopstate
    window.addEventListener 'popstate', (event) ->
      if updatingMapState
        return
      updatingMapState = true
      restoreMapPosition()
      map.setCenter(new mapboxgl.LngLat(center[0], center[1]))
      map.setZoom(zoom)
      map.setBearing(bearing)
      map.setPitch(pitch)
      updatingMapState = false

    # autocomplete
    $(() ->
      xhr = null
      $('#search-location').autoComplete
        delay: 500
        source: (term, suggest) ->
          console.log "term is #{term}"
          try
            xhr.abort()

          url = 'https://api.opencagedata.com/geocode/v1/json'
          params =
            q: term,
            key: '5aa73ae4e27b43248135d8817205efb4'

          successHandler = (data) ->
            transform = (result) ->
              if result.bounds
                northEast = result.bounds.northeast
                southWest = result.bounds.southwest
                # northEast = ol.proj.fromLonLat([northEast.lng, northEast.lat])
                # southWest = ol.proj.fromLonLat([southWest.lng, southWest.lat])
                minx = Math.min(northEast.lng, southWest.lng)
                miny = Math.min(northEast.lat, southWest.lat)
                maxx = Math.max(northEast.lng, southWest.lng)
                maxy = Math.max(northEast.lat, southWest.lat)
                return {
                  text: result.formatted,
                  extent: [[minx, miny], [maxx, maxy]]
                }
              else if result.geometry
                # geom = ol.proj.fromLonLat([result.geometry.lng, result.geometry.lat])
                geom = [result.geometry.lng, result.geometry.lat]
                return {
                  text: result.formatted,
                  extent: [[geom[0], geom[1]], [geom[0], geom[1]]]
                }
              else
                return null

            suggestions = data.results.map(transform).filter((x) -> x != null)
            suggest(suggestions);

          xhr = $.getJSON(url, params, successHandler)

        renderItem: (item, search) ->
          search = search.replace(/[-\/\\^$*+?.()|[\]{}]/g, '\\$&')
          re = new RegExp("(" + search.split(' ').join('|') + ")", "gi")
          "<div class=\"autocomplete-suggestion\" data-extent=\"#{JSON.stringify(item.extent)}\" data-lng=\"#{item.lng}\">#{item.text.replace(re, "<b>$1</b>")}</div>"

        onSelect: (e, term, item) ->
          extent = item.data('extent')
          map.fitBounds(extent, {
            linear: true
          })
    )
}
