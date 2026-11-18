export const PALETTE = {
  red:['红色','#ff0000'], orange:['橙色','#ff8800'], yellow:['黄色','#ffff00'], green:['绿色','#00aa00'],
  cyan:['青色','#00aaaa'], blue:['蓝色','#0055ff'], purple:['紫色','#aa00ff'], pink:['粉色','#ff88bb'],
};
export const NAMED_COLORS={white:'#ffffff',black:'#000000',light_gray:'#aaaaaa',gray:'#555555',brown:'#885533',lime:'#55ff00',magenta:'#ff00ff',light_blue:'#55bbff',...Object.fromEntries(Object.entries(PALETTE).map(([k,v])=>[k,v[1]]))};
export function quantizeHex(hex){
  if(typeof hex!=='string'||!/^#[0-9a-f]{6}$/i.test(hex))throw Error('需要 #RRGGBB 形式的颜色');
  return '#'+[1,3,5].map(i=>(Math.round(parseInt(hex.slice(i,i+2),16)/17)*17).toString(16).padStart(2,'0')).join('');
}
export function colorKey(hex){return 'display_custom:colors/'+quantizeHex(hex).slice(1);}
export function keyColor(key){
  if(typeof key!=='string'||!key.startsWith('display_custom:colors/'))return null;
  const code=key.slice('display_custom:colors/'.length);
  if(Object.hasOwn(NAMED_COLORS,code))return NAMED_COLORS[code];
  return /^[0-9a-f]{6}$/.test(code)&&quantizeHex('#'+code)==='#'+code?'#'+code:null;
}
export function allColorCodes(){const out=[];for(let r=0;r<16;r++)for(let g=0;g<16;g++)for(let b=0;b<16;b++)out.push([r,g,b].map(n=>n.toString(16).repeat(2)).join(''));return out;}
