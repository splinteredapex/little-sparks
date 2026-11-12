/* Little Sparks offline cache: serve from the cache straight away, refresh it in the background */
var CACHE='little-sparks-v6';
var SHELL=['./','./index.html','./manifest.webmanifest','./icon-192.png','./icon-512.png','./icon-maskable.png','./apple-touch-icon.png'];
self.addEventListener('install',function(e){
  e.waitUntil(caches.open(CACHE).then(function(c){return c.addAll(SHELL);}).then(function(){return self.skipWaiting();}));
});
self.addEventListener('activate',function(e){
  e.waitUntil(caches.keys().then(function(keys){
    return Promise.all(keys.filter(function(k){return k!==CACHE;}).map(function(k){return caches.delete(k);}));
  }).then(function(){return self.clients.claim();}));
});
self.addEventListener('fetch',function(e){
  var req=e.request;
  if(req.method!=='GET')return;
  var url=new URL(req.url);
  var ok=url.origin===self.location.origin||url.hostname==='fonts.googleapis.com'||url.hostname==='fonts.gstatic.com';
  if(!ok)return;
  e.respondWith(caches.open(CACHE).then(function(c){
    return c.match(req,{ignoreSearch:url.origin===self.location.origin}).then(function(hit){
      var net=fetch(req).then(function(res){if(res&&(res.ok||res.type==='opaque'))c.put(req,res.clone());return res;}).catch(function(){return hit;});
      return hit||net;
    });
  }));
});
