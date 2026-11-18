import {writeFile,mkdir} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
import {fullColorLibraryZip,colorLibraryFiles} from '../editor/src/resource-pack.js';
import {NAMED_COLORS,allColorCodes} from '../editor/src/palette.js';

const root=new URL('../',import.meta.url);
for(const [path,data] of Object.entries(colorLibraryFiles())){
  const target=new URL('resource-pack/'+path,root);
  await mkdir(new URL('./',target),{recursive:true});await writeFile(target,data);
}
const zip=fullColorLibraryZip();
for(const path of ['release/','src/main/resources/resourcepacks/'])await mkdir(new URL(path,root),{recursive:true});
for(const path of ['release/display-custom-colors-0.3.0.zip','src/main/resources/resourcepacks/display-custom-colors-0.3.0.zip'])await writeFile(new URL(path,root),zip);
const sha1=createHash('sha1').update(zip).digest('hex');
await writeFile(new URL('release/display-custom-colors-0.3.0.sha1',root),sha1+'\n');
await writeFile(new URL('release/color-library.json',root),JSON.stringify({namespace:'display_custom',prefix:'colors/',step:17,count:4096,named:NAMED_COLORS,keys:allColorCodes().map(code=>'display_custom:colors/'+code)},null,2)+'\n');
console.log(`Generated 4096 colors + ${Object.keys(NAMED_COLORS).length} aliases, ${zip.length} bytes; SHA-1 ${sha1}`);
console.log(fileURLToPath(new URL('release/display-custom-colors-0.3.0.zip',root)));
