var popup = require('../lib/popup'),
    escape = require('escape-html'),
    $ = require('jquery'),
    mapboxgl = require('mapbox-gl'),
    writable = false,
    makiValues = require('../../data/maki.json'),
    maki = '',
    turf = require('turf'),
    EditControl = require('./map-controls/editControl'),
    GridControl = require('./map-controls/gridControl'),
    loading = require('./loading.js');

for (var i = 0; i < makiValues.length; i++) {
    maki += '<option value="' + makiValues[i].icon + '">';
}

module.exports = function(context, readonly) {

    writable = !readonly;

    var editMode = false;
    var dataBeforeEdit = null;

    function map(selection) {

        context.map = new mapboxgl.Map({
            container: selection.node(), // container id
            style: {
                "version": 8,
                "sources": {
                    "base-map-source": {
                        "type": "raster",
                        "tiles": [
                            "https://a.tile.openstreetmap.org/{z}/{x}/{y}.png",
                            "https://b.tile.openstreetmap.org/{z}/{x}/{y}.png"
                        ],
                        "tileSize": 256
                    }
                },
                "layers": [{
                    "id": "base-map-layer",
                    "type": "raster",
                    "source": "base-map-source",
                    "minzoom": 0,
                    "maxzoom": 22
                }],
                "glyphs": "https://fonts.openmaptiles.org/{fontstack}/{range}.pbf",
            },
            center: [20, 46], // starting position
            zoom: 5 // starting zoom
        });

        var gridControl = new GridControl({ texts: context.texts })
        .on('confirm', function(confirmed) {
          if (confirmed) {
            loading.show();
          }
        })
        .on('done', function(geojson) {
          loading.hide();
          context.data.set({map: geojson}, 'map');
        });

        var editControl = new EditControl({ source: () => { return context.data.get('map'); } })
        .on('switch-to-edit', function() {
          dataBeforeEdit = context.data.get('map');
          context.map.getSource('geojson-source').setData({
            type: 'FeatureCollection',
            features: []
          });
          context.map.removeControl(gridControl);
          editMode = true;
        })
        .on('done', (data) => {
          context.data.set({map: data}, 'map');
          context.map.addControl(gridControl);
          editMode = false;
        })
        .on('cancel', () => {
          context.map.getSource('geojson-source').setData(dataBeforeEdit);
          dataBeforeEdit = null;
          context.map.addControl(gridControl);
          editMode = false;
        });

        context.map.addControl(new mapboxgl.NavigationControl());

        context.dispatch.on('open_serie.map', function() {
          context.map.addControl(editControl);
          context.map.addControl(gridControl);
        });

        context.dispatch.on('change.map', function(data) {
          if (data.obj && data.obj.map) {
            geojsonToLayer(data.obj.map);
          }
        });

        context.dispatch.on('beforeclear.map', () => {
          editControl.cancel();
        });
    }

    function geojsonToLayer(geojson) {

      var labels = [];

      geojson.features.forEach(function(feature) {
        var centroid = turf.centroid(feature);
        centroid.properties = feature.properties;
        labels.push(centroid);
      });

      if (!context.map.getSource('geojson-source')) {
        context.map.addSource("geojson-source", {
          "type": "geojson",
          "data": geojson
        });
        context.map.addLayer({
          "id": "geojson-layer",
          "type": "fill",
          "source": "geojson-source",
          "paint": {
              "fill-color": "#888",
              "fill-outline-color": "#000",
              "fill-opacity": 0.8
          }
        });
      } else {
        context.map.getSource("geojson-source").setData(geojson);
      }

      if (!context.map.getSource('label-source')) {
        context.map.addSource("label-source", {
          "type": "geojson",
          "data": {
            "type": "FeatureCollection",
            "features": labels
          }
        });
        context.map.addLayer({
          "id": "label-layer",
          "type": "symbol",
          "source": "label-source",
          "layout": {
            "text-field": "{SHEET}",
            "text-font": ["Open Sans Light"],
          }
        });
        context.map.on('click', 'label-layer', function(e) {
          if (editMode) {
            return;
          }
          context.dispatch.select_layer(e.features[0].properties['SHEET']);
        });
        context.map.on('mouseenter', 'label-layer', function () {
          if (editMode) {
            return;
          }
          context.map.getCanvas().style.cursor = 'pointer';
        });

        // Change it back to a pointer when it leaves.
        context.map.on('mouseleave', 'label-layer', function () {
          if (editMode) {
            return;
          }
          context.map.getCanvas().style.cursor = '';
        });
      } else {
        context.map.getSource("label-source").setData({
          "type": "FeatureCollection",
          "features": labels
        });
      }

    }

    return map;
};
