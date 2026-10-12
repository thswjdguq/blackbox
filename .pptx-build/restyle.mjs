import fs from 'node:fs/promises';
import {PresentationFile,FileBlob} from '@oai/artifact-tool';
import {finalizePresentation} from 'file:///C:/Users/myhyu/.codex/plugins/cache/openai-primary-runtime/presentations/26.909.12148/skills/presentations/container_tools/artifact_tool_utils.mjs';
const dir='C:/blackbox/.pptx-build/restyle';await fs.mkdir(dir+'/before',{recursive:true});await fs.mkdir(dir+'/after',{recursive:true});
const p=await PresentationFile.importPptx(await FileBlob.load('C:/blackbox/outputs/손에송잡고 프로젝트고도화.pptx'));
await fs.writeFile(dir+'/inspect.txt',(await p.inspect({kind:'slide,textbox,table,layout',maxChars:100000})).ndjson);
await fs.writeFile(dir+'/proto.json',JSON.stringify(p.toProto()));
for(let i=0;i<p.slides.items.length;i++){const b=await p.export({slide:p.slides.items[i],format:'png',scale:1});await fs.writeFile(dir+'/before/'+(i+1)+'.png',new Uint8Array(await b.arrayBuffer()));}
const snapshot=(await p.inspect({kind:'textbox,table',maxChars:150000})).ndjson.split('\n').filter(Boolean).map(x=>JSON.parse(x));
const navy='#163A50',teal='#078C87',font='Malgun Gothic';
for(const sl of p.slides.items)sl.background.fill='#FFFFFF';
for(const item of snapshot.filter(x=>x.kind==='textbox')){
 const a=p.resolve(item.id),[x,y,w,h]=item.bbox;
 a.text=item.text;
 const title=y<150, page=/^\d+$/.test(item.text.trim()),label=x<120&&w<320;
 a.text.style={typeface:font,fontSize:page?17:title?42:label?28:27,bold:title||label,color:page?'#6A8999':title?navy:label?teal:'#344B59',autoFit:'none'};
 if(title)a.position={left:70,top:55,width:1140,height:95};
 if(item.slide===1&&!page){if(item.text.includes('PMS')){a.position={left:78,top:204,width:1120,height:220};a.text.style={typeface:font,fontSize:60,bold:true,color:navy,autoFit:'none'};}else if(item.text==='Team Blackbox'){a.position={left:82,top:112,width:1100,height:55};a.text.style={typeface:font,fontSize:28,bold:true,color:teal};}else{a.position={left:82,top:540,width:1100,height:50};a.text.style={typeface:font,fontSize:26,color:'#526B78'};}}
}
const owners=[];
for(const item of snapshot.filter(x=>x.kind==='table')){
 const t=p.resolve(item.id);owners.push(item.slide);
 const nr=t.rows.length,nc=t.columns.items?.length??item.columns;
 console.log('TABLE',item.slide,nr,nc);
 t.borders.assign({fill:'#D6E5E9',width:0.7});
 for(let r=0;r<nr;r++)for(let c=0;c<nc;c++){
  const cell=t.getCell(r,c);cell.fill=r===0?navy:r%2?'#FFFFFF':'#F1F7F8';
  cell.text.style={typeface:font,fontSize:item.slide===10?24:25,color:r===0?'#FFFFFF':c===0?teal:'#344B59',bold:r===0||c===0};
 }
}
const out='C:/blackbox/outputs/손에송잡고_프로젝트고도화_디자인수정.pptx';
await(await PresentationFile.exportPptx(p)).save(dir+'/candidate.pptx');
const skill='C:/Users/myhyu/.codex/plugins/cache/openai-primary-runtime/presentations/26.909.12148/skills/presentations';
await finalizePresentation({workspaceDir:'C:/blackbox',candidatePath:dir+'/candidate.pptx',finalPath:out,pythonExecutable:'C:/Users/myhyu/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe',integrityValidatorPath:skill+'/container_tools/inspect_presentation_package_integrity.py',layoutValidatorPath:skill+'/container_tools/inspect_presentation_layout_geometry.py',layoutArgs:['--expected-slide-size-emu','12192000,6858000','--validate-heading-fit',...owners.flatMap(n=>['--require-native-table-slide',String(n)])],requiredNativeTableOwnerSlides:owners,fontPolicy:{basis:'design',families:[font]},verifyArtifactToolImport:true,receiptPath:dir+'/validation.json'});
const q=await PresentationFile.importPptx(await FileBlob.load(out));for(let i=0;i<q.slides.items.length;i++){let a=await q.export({slide:q.slides.items[i],format:'png',scale:1});await fs.writeFile(dir+'/after/'+(i+1)+'.png',new Uint8Array(await a.arrayBuffer()));}console.log('DONE');
