import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const postmanDir = path.dirname(fileURLToPath(import.meta.url));
const root = path.dirname(postmanDir);
const collectionPath = path.join(postmanDir, "Moneybags-Complete-Workflow.postman_collection.json");

function walk(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const fullPath = path.join(directory, entry.name);
    return entry.isDirectory() ? walk(fullPath) : [fullPath];
  });
}

function quotedValues(text = "") {
  return [...text.matchAll(/"([^"]+)"/g)].map(match => match[1]);
}

function joinRoute(base, route) {
  const joined = `${base || ""}/${route || ""}`.replaceAll("//", "/");
  return joined.length > 1 && joined.endsWith("/") ? joined.slice(0, -1) : joined;
}

function controllerRoutes(file) {
  const source = fs.readFileSync(file, "utf8");
  const classIndex = source.search(/\bclass\s+\w+/);
  const classPrefix = classIndex < 0 ? source : source.slice(0, classIndex);
  const classMappings = [...classPrefix.matchAll(/@RequestMapping\s*\(([\s\S]*?)\)/g)];
  const baseValues = classMappings.length ? quotedValues(classMappings.at(-1)[1]) : [];
  const base = baseValues[0] || "";
  const routes = [];
  const mappingPattern = /@(Get|Post|Put|Patch|Delete)Mapping\s*(?:\(([\s\S]*?)\))?/g;
  for (const match of source.slice(Math.max(0, classIndex)).matchAll(mappingPattern)) {
    const method = match[1].toUpperCase();
    const values = quotedValues(match[2]);
    routes.push({ method, path: joinRoute(base, values[0] || ""), file });
  }
  return routes;
}

function flattenRequests(items, output = []) {
  for (const item of items || []) {
    if (item.request) {
      const url = typeof item.request.url === "string" ? item.request.url : item.request.url?.raw;
      output.push({ method: item.request.method, url, name: item.name });
    }
    flattenRequests(item.item, output);
  }
  return output;
}

function collectionPathname(rawUrl) {
  return String(rawUrl || "")
    .replace(/^\{\{[^}]+\}\}/, "")
    .split("?")[0]
    .replace(/\{\{[^}]+\}\}/g, "{}");
}

function routePattern(route) {
  const escaped = route.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  return new RegExp(`^${escaped.replace(/\\\{[^/]+\\\}/g, "[^/]+")}$`);
}

const collection = JSON.parse(fs.readFileSync(collectionPath, "utf8"));
const requests = flattenRequests(collection.item).map(requestItem => ({
  ...requestItem,
  path: collectionPathname(requestItem.url)
}));

const serviceDirectories = fs.readdirSync(root, { withFileTypes: true })
  .filter(entry => entry.isDirectory() && entry.name.endsWith("-service"))
  .map(entry => path.join(root, entry.name, "src", "main", "java"))
  .filter(directory => fs.existsSync(directory));

const controllers = serviceDirectories
  .flatMap(walk)
  .filter(file => file.endsWith("Controller.java"));
const routes = controllers.flatMap(controllerRoutes);
const missing = routes.filter(route => {
  const pattern = routePattern(route.path);
  return !requests.some(requestItem => requestItem.method === route.method && pattern.test(requestItem.path));
});

const variables = collection.variable || [];
const duplicateVariables = variables
  .map(variable => variable.key)
  .filter((key, index, keys) => keys.indexOf(key) !== index);
const serialized = JSON.stringify(collection);
const referencedVariables = [...serialized.matchAll(/\{\{([A-Za-z0-9_.-]+)\}\}/g)].map(match => match[1]);
const declaredVariables = new Set(variables.map(variable => variable.key));
const dynamicVariables = new Set(["$guid", "$timestamp", "$randomUUID"]);
const unresolvedVariables = [...new Set(referencedVariables.filter(key => !declaredVariables.has(key) && !dynamicVariables.has(key)))];

console.log(`Validated ${requests.length} Postman requests against ${routes.length} Spring controller routes in ${controllers.length} controllers.`);
if (missing.length) {
  console.error("Missing controller routes:");
  for (const route of missing) console.error(`- ${route.method} ${route.path} (${path.relative(root, route.file)})`);
}
if (duplicateVariables.length) console.error(`Duplicate collection variables: ${[...new Set(duplicateVariables)].join(", ")}`);
if (unresolvedVariables.length) console.error(`Unresolved collection variables: ${unresolvedVariables.join(", ")}`);

if (missing.length || duplicateVariables.length || unresolvedVariables.length) process.exitCode = 1;
