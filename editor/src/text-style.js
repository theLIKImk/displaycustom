export const TEXT_STYLE_DEFAULTS=Object.freeze({color:'#ffffff',opacity:255,backgroundColor:'#000000',backgroundOpacity:0,shadow:true,seeThrough:false,alignment:'center',lineWidth:200,bold:false,italic:false});
export const textStyle=part=>({...TEXT_STYLE_DEFAULTS,...part.textStyle});
export function validateTextStyle(value){
  if(value==null)return undefined;
  if(typeof value!=='object'||Array.isArray(value)||Object.keys(value).some(k=>!Object.hasOwn(TEXT_STYLE_DEFAULTS,k)))throw Error('textStyle 需要有效的文字样式对象');
  const style={...TEXT_STYLE_DEFAULTS,...value};
  for(const key of ['color','backgroundColor']){
    if(typeof style[key]!=='string'||!/^#[0-9a-fA-F]{6}$/.test(style[key]))throw Error('文字和背景颜色必须为 #RRGGBB');
    style[key]=style[key].toLowerCase();
  }
  for(const key of ['opacity','backgroundOpacity'])if(!Number.isInteger(style[key])||style[key]<0||style[key]>255)throw Error('文字和背景不透明度需为 0–255 的整数');
  for(const key of ['shadow','seeThrough','bold','italic'])if(typeof style[key]!=='boolean')throw Error('文字样式开关必须为布尔值');
  if(!['left','center','right'].includes(style.alignment))throw Error('文字对齐方式必须为 left、center 或 right');
  if(!Number.isInteger(style.lineWidth)||style.lineWidth<1||style.lineWidth>1000)throw Error('文字换行宽度需为 1–1000 的整数');
  return style;
}
export const opacityPercent=value=>Math.round(value/255*100);
export function opacityByte(percent){
  if(!Number.isFinite(percent)||percent<0||percent>100)throw Error('不透明度需为 0–100%');
  return Math.round(percent/100*255);
}
export function textLayout(text,style,measure){
  const lines=[],limit=style.lineWidth*3;
  for(const paragraph of text.split('\n')){
    let line='';for(const char of paragraph){if(line&&measure(line+char)>limit){lines.push(line);line='';}line+=char;}lines.push(line);
  }
  return {lines,width:Math.max(36,...lines.map(line=>Math.ceil(measure(line))+18)),height:Math.max(36,lines.length*30+12)};
}
