/**
  Copyright (c) 2015, 2024, Oracle and/or its affiliates.
  Licensed under The Universal Permissive License (UPL), Version 1.0
  as shown at https://oss.oracle.com/licenses/upl/

*/

'use strict';

const path = require('path');

module.exports = function (configObj) {
  return new Promise((resolve, reject) => {
    console.log('Running before_serve hook.');
    // ojet custom connect and serve options
    // { connectOpts, serveOpts } = configObj;
    // const express = require('express');
    // const http = require('http');
    // pass back custom http
    // configObj['http'] = http;
    // pass back custom express app
    // configObj['express'] = express();
    // pass back custom options for http.createServer
    // const serverOptions = {...};
    // configObj['serverOptions'] = serverOptions;
    // pass back custom server
    // configObj['server'] = http.createServer(serverOptions, express());
    // const tinylr = require('tiny-lr');
    // pass back custom live reload server
    // configObj['liveReloadServer'] = tinylr({ port: PORT });
    // pass back a replacement set of middleware
    // configObj['middleware'] = [...];
    // pass back a set of middleware that goes before the default middleware
    // configObj['preMiddleware'] = [...];
    // pass back a set of middleware that goes after the default middleware
    // configObj['postMiddleware'] = [...];
    // Keep client-side routes refreshable in the local OJET server. Static
    // assets still use the default middleware; extensionless GET requests fall
    // back to the application shell.
    configObj.postMiddleware = [function spaFallback(req, res, next) {
      const acceptsHtml = (req.headers.accept || '').includes('text/html');
      if (req.method === 'GET' && acceptsHtml && !path.extname(req.path)) {
        res.sendFile(path.join(process.cwd(), 'web', 'index.html'));
        return;
      }
      next();
    }];
    resolve(configObj);
  });
};
