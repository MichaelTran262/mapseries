var vex = require('vex-js'),
    vexDialog = require('vex-js/js/vex.dialog.js'),
    $ = require('jquery');

require('fabric');

module.exports = {
  Control: L.Control.extend({
    options: {
      position: 'topright',
      labelGap: 5,
      canvasPadding: 30,
      pointRadius: 5,
      fillColor: 'white',
      strokeColor: 'black',
      hoverFillColor: 'red',
      hoverStrokeColor: 'red',
      fontSize: 12
    },

    initialize: function(texts, options) {
      L.Control.prototype.initialize.call(this, options);
      vex.defaultOptions.className = 'vex-theme-os';
      vexDialog.defaultOptions.showCloseButton = true;
      this.texts = texts;
      this._cols = 5;
      this._rows = 5;
      this._ltCoor = [90, -180];
      this._rtCoor = [90, 180];
      this._lbCoor = [-90, -180];
      this._rbCoor = [-90, 180];
      this._excludedRects = new Set();
      this._proj = 4326;
    },

    onAdd: function(map) {
      this._map = map;
      var container = L.DomUtil.create('div', 'leaflet-control-grid-container');
      var button = L.DomUtil.create('div', 'leaflet-control-grid-action');
      L.DomEvent
        .on(button, 'click', L.DomEvent.stopPropagation)
        .on(button, 'click', L.DomEvent.preventDefault)
        .on(button, 'click', this.onAction, this);

      container.appendChild(button);
      return container;
    },

    onAction: function() {
      var controlInstance = this;
      var input = '<canvas id="gridCanvas" width="400" height="300"></canvas>' +
                  '<label for="input-cols">' + this.texts.colsCount + ':</label>' +
                  '<input type="number" id="input-cols" value="' + this._cols + '">' +
                  '<label for="input-rows">' + this.texts.rowsCount + ':</label>' +
                  '<input type="number" id="input-rows" value="' + this._rows + '">' +
                  '<div id="bttn-proj">EPSG: ' + this._proj + '</div>';


      vexDialog.open({
        message: this.texts.gridDialogTitle,
        input: input,
        callback: this.confirmDialog.bind(this),
        afterOpen: function() {
          controlInstance.initCanvas.call(controlInstance);
          controlInstance.initDialog.call(controlInstance, this.content);
          controlInstance.updateCanvas.call(controlInstance);
        }
      });
    },

    initCanvas: function() {
      this._canvas = new fabric.Canvas('gridCanvas');
      this._canvas.selection = false;
      this._canvas.renderOnAddRemove = false;
      this._canvas.on('mouse:down', function(options) {
        if (options.target && options.target.click instanceof Function) {
          options.target.click(this);
        }
      });
      this._canvas.on('mouse:over', function(options) {
        if (options.target && options.target.over instanceof Function) {
          options.target.over(this);
        }
      });
      this._canvas.on('mouse:out', function(options) {
        if (options.target && options.target.out instanceof Function) {
          options.target.out(this);
        }
      });
    },

    updateCanvas: function() {
      var left = this.options.canvasPadding;
      var right = this._canvas.getWidth() - this.options.canvasPadding;
      var top = this.options.canvasPadding;
      var bottom = this._canvas.getHeight() - this.options.canvasPadding;
      var width = right - left;
      var height = bottom - top;
      var rectWidth = width / this._cols;
      var rectHeight = height / this._rows;
      var _this = this;
      this._canvas.clear();

      for (var y = 0; y < this._rows; y++) {
        for (var x = 0; x < this._cols; x++) {
          var rect = new fabric.Rect({
            id: '' + x + '-' + y,
            left: left + x * rectWidth,
            top: top + y * rectHeight,
            width: rectWidth,
            height: rectHeight,
            fill: this.options.fillColor,
            stroke: this.options.strokeColor,
            selectable: false,
            included: true
          });
          rect.click = function(canvas) {
            if (this.included) {
              this.setFill(canvas.backgroundColor);
              this.setStroke(canvas.backgroundColor);
              this.included = false;
              _this._excludedRects.add(this.id);
            } else {
              this.setFill(_this.options.fillColor);
              this.setStroke(_this.options.strokeColor);
              this.included = true;
              _this._excludedRects.delete(this.id);
            }
          };
          if (this._excludedRects.has(rect.id)) {
            rect.setFill(this._canvas.backgroundColor);
            rect.setStroke(this._canvas.backgroundColor);
            rect.included = false;
          }
          this._canvas.add(rect);
        }
      }

      var ltPoint = new fabric.Circle({
        left: left - this.options.pointRadius,
        top: top - this.options.pointRadius,
        data: this._ltCoor
      });
      var rbPoint = new fabric.Circle({
        left: right - this.options.pointRadius,
        top: bottom - this.options.pointRadius,
        data: this._rbCoor
      });

      var points = [ltPoint, rbPoint];
      points.forEach(function(point) {
        point.set({
          radius: this.options.pointRadius,
          fill: this.options.strokeColor,
          stroke: this.options.strokeColor,
          selectable: false
        });
        point.over = function(canvas) {
          this.set({
            fill: _this.options.hoverFillColor,
            stroke: _this.options.hoverStrokeColor
          });
          canvas.renderAll();
        };
        point.out = function(canvas) {
          this.set({
            fill: _this.options.strokeColor,
            stroke: _this.options.strokeColor
          });
          canvas.renderAll();
        };
        point.click = function(canvas) {
          _this.showCoorsDialog.call(_this, this.data);
        };
        this._canvas.add(point);
      }, this);

      var ltLabel = new fabric.Text('' + this._ltCoor[0].toFixed(2) + ', ' + this._ltCoor[1].toFixed(2), {
        left: left - this.options.pointRadius,
        fontSize: this.options.fontSize,
        selectable: false
      });
      ltLabel.setTop(top - this.options.pointRadius - this.options.labelGap - ltLabel.getHeight());

      var rbLabel = new fabric.Text('' + this._rbCoor[0].toFixed(2) + ', ' + this._rbCoor[1].toFixed(2), {
        top: bottom + this.options.pointRadius + this.options.labelGap,
        textAlign: 'right',
        fontSize: this.options.fontSize,
        selectable: false
      });
      rbLabel.setLeft(right + this.options.pointRadius - rbLabel.getWidth());

      var labels = [ltLabel, rbLabel];
      labels.forEach(function(label) {
        label.set({
          fillColor: this.options.fillColor,
          strokeColor: this.options.strokeColor
        });
        this._canvas.add(label);
      }, this);
      this._canvas.renderAll();
    },

    initDialog: function(dialogContent) {
      this._inputCols = document.getElementById('input-cols');
      this._inputRows = document.getElementById('input-rows');
      var bttnProj = document.getElementById('bttn-proj');

      this._cols = this._inputCols.value;
      this._rows = this._inputRows.value;


      L.DomEvent
        .on(this._inputCols, 'change', this.onColsChanged, this)
        .on(this._inputRows, 'change', this.onRowsChanged, this)
        .on(bttnProj, 'click', this.onBttnProjClick, this);
    },

    confirmDialog: function(data) {
      if (data === false) {
        return;
      }
      var left = this._ltCoor[1],
          top = this._ltCoor[0],
          xstep = (this._rbCoor[1] - this._ltCoor[1]) / this._cols,
          ystep = (this._ltCoor[0] - this._rbCoor[0]) / this._rows;

      var polygons = [];
      for (var x = 0; x < this._cols; x++) {
        for (var y = 0; y < this._rows; y++) {
          var id = '' + x + '-' + y;
          if (this._excludedRects.has(id)) {
            continue;
          }
          var lt = L.latLng(top - ystep*y, left + xstep*x);
          var rt = L.latLng(top - ystep*y, left + xstep*(x+1));
          var rb = L.latLng(top - ystep*(y+1), left + xstep*(x+1));
          var lb = L.latLng(top - ystep*(y+1), left + xstep*x);
          polygons.push(lt, rt, rb, lb);
        }
      }
      this.transformCoors(polygons, function(coors) {
        var polygons = [];
        for (var i = 0; i < coors.length;) {
          pcoors = [];
          for (var j = 0; j < 4; i++,j++) {
            var coor = coors[i];
            pcoors.push(L.latLng(coor.y, coor.x));
          }
          polygons.push(L.polygon(pcoors));
        }
        this._map.fire('draw:created', { layers: polygons});
      });
    },

    transformCoors: function(coors, callback, result) {
      result = result || [];
      var batch = coors.slice(0, 20);
      var rest = coors.slice(20, coors.length);

      var callbackInternal = function(r) {
        result = result.concat(r);
        if (rest.length) {
          this.transformCoors.call(this, rest, callback, result);
        } else {
          callback.call(this, result);
        }
      };

      this.transformCoorsInternal(batch, callbackInternal.bind(this));
    },

    transformCoorsInternal: function(coors, callback) {
      var _this = this;
      var out = [];
      coors.forEach(function (coor) {
        out.push('' + coor.lng + ',' + coor.lat);
      });
      out = out.join(';');
      $.ajax({
        url: 'https://epsg.io/trans',
        jsonp: 'callback',
        dataType: 'jsonp',
        data: {
          data: out,
          s_srs: this._proj,
          t_srs: 4326
        },
        success: function(response) {
          callback.call(_this, response);
        },
        error: function(xhr, msg, exception) {
          console.error(xhr);
          console.error(msg);
          console.error(exception);
          alert(msg);
          callback(null);
        }
      });
    },

    onColsChanged: function() {
      this._cols = this._inputCols.value;
      this._excludedRects.clear();
      this.updateCanvas();
    },

    onRowsChanged: function(e) {
      this._rows = this._inputRows.value;
      this._excludedRects.clear();
      this.updateCanvas();
    },

    onProjChanged: function(proj) {
      this._proj = proj;
      document.getElementById('bttn-proj').innerHTML = 'EPSG: ' + proj;
    },

    onBttnProjClick: function(e) {
      vexDialog.open({
        message: 'Projekce',
        afterOpen: this.initProjDialog.bind(this),
        input: '<input type="text" name="proj" id="input-proj">',
        callback: this.confirmProjDialog.bind(this)
      });
    },

    initProjDialog: function() {
      var inputProj  = document.getElementById('input-proj');
      $(inputProj).autocomplete({
        source: function(request, response) {
          $.ajax({
            url: 'https://epsg.io',
            jsonp: 'callback',
            dataType: 'jsonp',
            data: {
              q: request.term,
              format: 'json',
              trans: '1'
            },
            success: function(data) {
              if (data.status == 'ok') {
                var result = [];
                data.results.forEach(function(it) {
                  result.push(it.code + ' ' + it.name);
                });
                response(result);
              } else {
                console.error(data);
                response([]);
              }
            },
            error: function(data) {
              console.error(data);
              response([]);
            }
          });
        }
      });
    },

    confirmProjDialog: function(data) {
      if (data) {
        var projParser = new RegExp(/^(\d+).*/);
        this.onProjChanged(projParser.exec(data.proj)[1]);
      }
    },

    showCoorsDialog: function(dataModel) {
      var _this = this;
      var input = '<label for="input-north">Severn?? sou??adnice:<label><input type="text" name="north" id="input-north">'
                + '<label for="input-east">V??chodn?? sou??adnice:</label><input type="text" name="east" id="input-east">';

      vexDialog.open({
        message: 'S??radnice',
        input: input,
        afterOpen: this.initCoorsDialog.bind(this),
        callback: function(data) {
          _this.confirmCoorsDialog.call(_this, data, dataModel);
        }
      });
    },

    initCoorsDialog: function() {
      var inputNorth = document.getElementById('input-north'),
          inputEast  = document.getElementById('input-east');

      inputNorth.addEventListener('keydown', this.onCoorsKeyEvent);
      inputEast.addEventListener('keydown', this.onCoorsKeyEvent);
    },

    confirmCoorsDialog: function(data, dataModel) {
      if (data) {
        dataModel[0] = this.coorStrToNum(data.north);
        dataModel[1] = this.coorStrToNum(data.east);
        this.updateCanvas();
      }
    },

    onCoorsKeyEvent: function(e) {
      var carret = [e.target.selectionStart, e.target.selectionEnd];
      var start = e.target.value.substring(0, carret[0]);
      var end = e.target.value.substring(carret[1]);

      var contains = function(x, y) { return x.indexOf(y) > -1; }
      var isEmpty = function(x) { return !x || 0 === x.length };
      var isDigit = function(x) { return (x >= '0' && x <= '9') || x == '.' || x == ',' };

      var startContainsDegree = contains(start, '??');
      var startContainsMinute = contains(start, "'");
      var startContainsSecond = contains(start, '"');
      var startIsEmpty = isEmpty(start);

      var endContainsMinus = contains(end, '-');
      var endContainsDegree = contains(end, '??');
      var endContainsMinute = contains(end, "'");
      var endContainsSecond = contains(end, '"');

      var isMinus = e.keyCode == 109;
      var isNumber = e.keyCode >= 96 && e.keyCode <= 105;
      var isDecimalSep = e.keyCode == 190 || e.keyCode == 188;
      var isSpace = e.keyCode == 32;
      var isNavigation = e.keyCode == 37 /* LEFT */
        || e.keyCode == 39 /* RIGHT */
        || e.keyCode == 36 /* HOME */
        || e.keyCode == 35; /* END */
      var isTab = e.keyCode == 9;
      var isRemove = e.keyCode == 8 /* BACKSPACE */
        || e.keyCode == 46; /* DELETE */
      var isCopyPaste = (e.ctrlKey && e.keyCode == 67 /* C */)
        || (e.ctrlKey && e.keyCode == 86 /* V */);
      var isMarkAll = e.ctrlKey && e.keyCode == 65 /* A */;

      if (isNavigation || isTab || isRemove || isCopyPaste || isMarkAll) {
        // Preserves default behavior
        return;
      }

      if (startContainsSecond) {
        // we do not allow adding any new character
        e.preventDefault();
        return;
      }

      if (isMinus && startIsEmpty && !endContainsMinus) {
        // Preserves default behavior
        return;
      } else if (isNumber) {
        // Preserves default behavior
        return;
      } else if (isDecimalSep) {
        var i = carret[0];
        var j = carret[0];
        var value = e.target.value;

        while ((i > 0 && isDigit(value[i])) || (i == value.length)) i--;
        while (j < value.length && isDigit(value[j])) j++;

        if (!isDigit(value[i])) i++;
        if (!isDigit(value[j])) j--;

        var number = value.substring(i, j+1);

        if (!contains(number, '.') && !contains(number, ',')) {
          e.target.value = start + '.' + end;
        }

      } else if (isSpace) {
        var changed = false;
        if (startContainsMinute && !endContainsSecond) {
          e.target.value = start + '"' + end;
          changed = true;
        } else if (startContainsDegree && !startContainsMinute && !endContainsMinute) {
          e.target.value = start + "'" + end;
          changed = true;
        } else if (!startContainsDegree && !endContainsDegree) {
          e.target.value = start + '??' + end;
          changed = true;
        }

        if (changed) {
          e.target.selectionStart = carret[0] + 1;
          e.target.selectionEnd = carret[0] + 1;
        }
      }
      e.preventDefault();
    },

    coorStrToNum: function(coor) {
      var re = new RegExp(/^\s*(-?\d+(\.\d+)?)\s*([??|\s]\s*(\d+(\.\d+)?)?)?\s*(['|\s]\s*(\d+(\.\d+)?)?)?\s*"?\s*$/);
      var matches = re.exec(coor);
      var grad = parseInt(matches[1]);
      var min = parseInt(matches[4]);
      var sec = parseInt(matches[7]);
      min = min || 0;
      sec = sec || 0;
      return grad + min / 60.0 + sec / 3600.0;
    }
  })
}
