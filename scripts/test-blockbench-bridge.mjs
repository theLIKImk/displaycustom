import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {importProject,exportProject} from '../blockbench_plugin/bridge.mjs';
import {nodeMatrix} from '../editor/src/groups.js';
import {validateModel} from '../editor/src/model.js';

const read=path=>validateModel(JSON.parse(readFileSync(new URL('../'+path,import.meta.url),'utf8')));
const files=['docs/examples/simple_cube.json','docs/examples/ai_items.json',
 'src/test/resources/fixtures/advanced_demo.json','src/main/resources/models/testdoll.json',
 'src/main/resources/models/whale_girl.json'];
let poses=0;
for(const file of files){
  const original=read(file);
  const restored=exportProject(importProject(original));
  assert.equal(restored.name,original.name);
  assert.deepEqual(restored.info,original.info);
  for(const [index,part] of original.parts.entries()){
    assert.equal(restored.parts[index].itemModel,part.itemModel);
    assert.equal(restored.parts[index].heldItem,part.heldItem);
    assert.deepEqual(restored.parts[index].textStyle,part.textStyle);
  }
  for(const [name,clip] of Object.entries(original.animations??{})){
    const output=restored.animations[name];
    for(const field of ['itemUse','parallel','tags','sounds','loop'])assert.deepEqual(output[field],clip[field]);
    for(const [node,frames] of Object.entries(clip.tracks))for(const frame of frames){
      const expected=Object.fromEntries(['visible','text','textStyle'].filter(key=>frame[key]!=null).map(key=>[key,frame[key]]));
      if(Object.keys(expected).length){
        const actual=output.tracks[node].find(key=>Math.abs(key.time-frame.time)<1e-6);
        for(const [key,value] of Object.entries(expected))assert.deepEqual(actual[key],value);
      }
    }
    for(let n=0;n<=16;n++)for(const [index,part] of original.parts.entries()){
      const t=n*clip.duration/16;
      const a=nodeMatrix(original,part,[clip],t,false).elements;
      const b=nodeMatrix(restored,restored.parts[index],[output],t,false).elements;
      assert(a.every((value,axis)=>Math.abs(value-b[axis])<2e-5),file+' '+name+' '+part.id+' @ '+t);
      poses++;
    }
  }
}
const scene=importProject(read(files[0]));
scene.nodes[0].origin[0]+=16;
scene.nodes[0].box.from[0]+=16;scene.nodes[0].box.to[0]+=16;
assert.equal(exportProject(scene).parts[0].position[0],scene.nodes[0].origin[0]/16);
console.log('Round-trip geometry and fields verified for '+files.length+' models and '+poses+' sampled poses.');
