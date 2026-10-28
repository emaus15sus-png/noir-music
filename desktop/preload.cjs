// The only bridge between the UI and the rest of the app: no Node access in the page itself.
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('noir', {
  engine: (route, params) => ipcRenderer.invoke('engine', route, params),
  load: () => ipcRenderer.invoke('store:load'),
  save: (key, value) => ipcRenderer.invoke('store:save', key, value),
  tv: (action, route) => ipcRenderer.invoke('tv', action, route),
});
