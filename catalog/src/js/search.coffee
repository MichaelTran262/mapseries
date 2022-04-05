import 'bootstrap'
import mapboxgl from 'mapbox-gl'
import $ from 'jquery'
import bootbox from 'bootbox'
import loading from 'loading'
import turf from 'turf'
import Loader from 'loader'
import Template from 'closure/template/template'
import Series from 'model/series'
import 'autocomplete'


class Search

  constructor: (config) ->
    @map = null
    @series = null
    @seriess = null
    @template = new Template();
    @config = config

    @initMap()

    loader = new Loader()
    @seriess = loader.loadSeriess(@config['series']);
    @setSeries(@seriess[0])

    # initialize region switcher
    regions = Series.getRegions(@seriess)
    @updateRegions(regions)

    $('#regionSelect').trigger('change')


  initMap: ->
    # default zoom, center and rotation
    zoom = 5
    center = [20, 46]
    bearing = 0
    pitch = 0

    if window.location.hash != ''
      # try to restore center, zoom-level and rotation from the URL
      hash = window.location.hash.replace('#map=', '')
      parts = hash.split('/')
      if parts.length == 5
        zoom = parseInt(parts[0], 10)
        center = [
          parseFloat(parts[1]),
          parseFloat(parts[2])
        ]
        bearing = parseFloat(parts[3])
        pitch = parseFloat(parts[4])

    @map = new mapboxgl.Map
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
                "serie-source":
                  "type": "geojson"
                  "data": {
                    "type": "FeatureCollection",
                    "features": []
                  }
                "serie-source-labels":
                  "type": "geojson"
                  "data": {
                    "type": "FeatureCollection",
                    "features": []
                  }
                "serie-source-marked":
                  "type": "geojson"
                  "data": {
                    "type": "FeatureCollection",
                    "features": []
                  }
                "serie-source-labels-marked":
                  "type": "geojson"
                  "data": {
                    "type": "FeatureCollection",
                    "features": []
                  }

            "layers": [
              {
                "id": "base-map-layer"
                "type": "raster"
                "source": "base-map-source"
                "minzoom": 0
                "maxzoom": 22
              }
              {
                "id": "serie-layer"
                "type": "fill"
                "source": "serie-source"
                "paint": {
                    "fill-color": "#888"
                    "fill-outline-color": "#000"
                    "fill-opacity": 0.4
                }
              }
              {
                "id": "serie-layer-labels"
                "type": "symbol"
                "source": "serie-source-labels"
                "layout": {
                  "text-field": "{SHEET}"
                  "text-font": ["Open Sans Light"]
                }
              }
              {
                "id": "serie-layer-marked"
                "type": "fill"
                "source": "serie-source-marked"
                "paint": {
                    "fill-color": "#00d583"
                    "fill-opacity": 0.6
                }
              }
              {
                "id": "serie-layer-labels-marked"
                "type": "symbol"
                "source": "serie-source-labels-marked"
                "paint": {
                    "text-color": "#ffffff"
                    "text-halo-color": "#00d583"
                    "text-halo-width": 4
                }
                "layout": {
                  "text-field": "{SHEET}"
                  "text-font": ["Open Sans Light"]
                  "text-max-width": 20
                }
              }
            ]
            "glyphs": "https://fonts.openmaptiles.org/{fontstack}/{range}.pbf"
        center: center
        zoom: zoom
        bearing: bearing
        pitch: pitch

    @map.addControl(new mapboxgl.NavigationControl({showCompass: false}), 'top-left')
    @map.dragRotate.disable()
    @map.touchZoomRotate.disableRotation()

    shouldUpdate = true
    updatePermalink = () =>
      if not shouldUpdate
        # do not update the URL when the view was changed in the 'popstate' handler
        return

      center = @map.getCenter()
      z = @map.getZoom()
      x = Math.round(center.lng * 100) / 100
      y = Math.round(center.lat * 100) / 100
      b = Math.round(@map.getBearing() * 100) / 100
      p = Math.round(@map.getPitch() * 100) / 100
      hash = "#map=#{z}/#{x}/#{y}/#{b}/#{p}"
      state = {
        zoom: @map.getZoom()
        center: [@map.getCenter().lng, @map.getCenter().lat]
        bearing: @map.getBearing()
        pitch: @map.getPitch()
      }
      window.history.pushState(state, 'map', hash)

    @map.on('moveend', updatePermalink)

    @map.on 'click', 'serie-layer-labels', (e) =>
      sheet = e.features[0]
      @setActiveSheet(sheet)

    @map.on 'click', 'serie-layer', (e) =>
      sheet = e.features[0]
      @setActiveSheet(sheet)

    @map.on 'mouseenter', 'serie-layer', () => @map.getCanvas().style.cursor = 'pointer'
    @map.on 'mouseleave', 'serie-layer', () => @map.getCanvas().style.cursor = ''

    # restore the view state when navigating through the history, see
    # https://developer.mozilla.org/en-US/docs/Web/API/WindowEventHandlers/onpopstate
    window.addEventListener 'popstate', (event) =>
      if event.state == null
        return

      shouldUpdate = false
      center = event.state.center
      @map.setCenter(new mapboxgl.LngLat(center[0], center[1]))
      @map.setZoom(event.state.zoom)
      @map.setBearing(event.state.bearing)
      @map.setPitch(event.state.pitch)
      shouldUpdate = true

    # autocomplete
    $(() =>
      xhr = null
      $('#search-location').autoComplete
        delay: 500
        source: (term, suggest) ->
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

        onSelect: (e, term, item) =>
          extent = item.data('extent')
          @map.fitBounds(extent, {
            linear: true
          })
    )

  updateRegions: (regions) ->
    select = $('#regionSelect')
    select.off()
    select.empty()

    regions.forEach (region) ->
      select.append $('<option>', {
        value: region,
        text: region
      })

    select.on 'change', => @setRegion(select.val())

  setRegion: (region) ->
    seriess = null
    if region
      seriess = (serie for serie in @seriess when serie.title.startsWith(region))
    else
      seriess = @seriess

    # update grid switcher
    grids = Series.getGrids(seriess)
    @updateGrids(grids, region)

  updateGrids: (grids, region) ->
    select = $('#gridSelect')
    select.off()
    select.empty()

    grids.forEach (grid) ->
      visTitle = if region then grid.getShortTitle() else grid.title
      select.append $('<option>', {
        'value': grid.title
        'text': visTitle
      })

    value = if region then grids.values().next().value.title else ''
    select.on 'change', () =>
      gridTitle = select.val()
      grid = null
      grids.forEach (g) -> if g.title == gridTitle then grid = g
      regSelect = $('#regionSelect')
      region = regSelect.val()
      @setGrid(grid, region)

    select.val(value)
    select.trigger('change')

  setGrid: (grid, region) ->
    i = 0
    zip = (x) -> { val: x, index: i++ }

    seriess = (zip(x) for x in @seriess)

    if grid or region
      seriess = seriess.filter (series) ->
        (!grid || series.val.grid == grid) && (!region || series.val.title.startsWith(region))

    @updateSeriess(seriess, region)

  setSeries: (series) ->
    @setActiveSheet(null)
    @series = series

    loading.show()
    $.ajax {
      type: 'GET'
      url: "/server/data/geojson/#{@series.layer}.json"
      dataType: "json",
      success: (geojson) =>
        loading.hide()

        labels = []

        geojson.features.forEach (feature) ->
          centroid = turf.centroid(feature)
          centroid.properties = feature.properties
          centroid.properties['__GEOMETRY'] = feature.geometry;
          labels.push(centroid)

        @map.getSource("serie-source").setData(geojson)

        @map.getSource("serie-source-labels").setData({
          "type": "FeatureCollection",
          "features": labels
        })

      error: (err) ->
        loading.hide()
        bootbox.alert {
          size: 'large'
          title: "Error"
          message: "Server returned code #{err.status} with message: #{err.responseText}"
        }
    }

    editBttn = $('#edit-bttn')
    editBttn.removeClass('hidden')
    editBttn.attr('href', "/edit/#mapserie=#{series.id}")

  setActiveSheet: (sheet) ->
    html = null
    if !sheet
      @map.getSource("serie-source-labels-marked")?.setData({
        "type": "FeatureCollection",
        "features": []
      })
      @map.getSource("serie-source-marked")?.setData({
        "type": "FeatureCollection",
        "features": []
      })
      html = $('')
      @template.setVisible(false);
    else
      centroid = turf.centroid(sheet)
      centroid.properties = sheet.properties

      @map.getSource("serie-source-labels-marked").setData({
        "type": "FeatureCollection",
        "features": [centroid]
      })
      @map.getSource("serie-source-marked").setData({
        "type": "FeatureCollection",
        "features": [sheet]
      })

      @template.showSheet(sheet, @series, @map)


  updateSeriess: (seriess, region) ->
    select = $('#seriesSelect')
    select.off()
    select.empty()

    seriess.forEach (series) ->
      visTitle = if region then series.val.getShortTitle() else series.val.title
      select.append $('<option>', {
        value: series.index
        text: visTitle
      })

    select.on 'change', () => @setSeries(@seriess[select.val()])
    select.val(seriess[0].index)
    select.trigger('change')

export default Search
