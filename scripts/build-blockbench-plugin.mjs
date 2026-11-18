import {createRequire} from 'node:module';
import {mkdir,readFile,writeFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';

const root=new URL('../',import.meta.url);
const require=createRequire(new URL('editor/package.json',root));
const {build}=require('esbuild');
const {zipSync}=require('fflate');
const path=name=>fileURLToPath(new URL(name,root));
await mkdir(path('release'),{recursive:true});
const entry='release/display_custom_bridge.js';
await build({entryPoints:[path('blockbench_plugin/plugin.mjs')],outfile:path(entry),bundle:true,
  format:'iife',platform:'browser',target:'chrome120',charset:'utf8',legalComments:'none'});
await writeFile(path('release/display-custom-blockbench-0.1.0.zip'),zipSync({
  'display_custom_bridge.js':await readFile(path(entry)),
  'BLOCKBENCH.md':await readFile(path('docs/blockbench-bridge.md'))
},{level:6}));
console.log('Blockbench 插件已生成：'+path(entry));
