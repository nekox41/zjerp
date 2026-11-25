// Shared by browsing and drag/drop. No DOM or framework dependency.
export function formatUploadSize(bytes){
 if(bytes>=1024*1024)return (bytes/(1024*1024)).toFixed(1)+' MB'
 if(bytes>=1024)return (bytes/1024).toFixed(1)+' KB'
 return bytes+' B'
}
function accepted(file,accept){
 const rules=accept.toLowerCase().split(',').map(x=>x.trim()).filter(Boolean)
 if(!rules.length)return true
 const name=file.name.toLowerCase(),type=(file.type||'').toLowerCase()
 return rules.some(rule=>rule.startsWith('.')?name.endsWith(rule):rule.endsWith('/*')?type.startsWith(rule.slice(0,-1)):type===rule)
}
const fingerprint=f=>[f.name,f.size,f.lastModified].join('\u0000')
export function collectUploadFiles(current,incoming,{multiple=false,maxFiles=1,existingCount=0,maxBytes=25*1024*1024,accept=''}={}){
 const errors=[],files=multiple?[...current]:[],seen=new Set(files.map(fingerprint))
 let added=0
 for(const f of incoming){
  if(!f.size){errors.push(f.name+'：文件为空');continue}
  if(!accepted(f,accept)){errors.push(f.name+'：不支持此文件格式');continue}
  // The generic file service permits DOCX up to 100MB, other files 25MB.
  const limit=typeof maxBytes==='function'?maxBytes(f):maxBytes
  if(f.size>limit){errors.push(f.name+'：超过 '+formatUploadSize(limit)+' 限制');continue}
  if(seen.has(fingerprint(f))){errors.push(f.name+'：已选择');continue}
  if(files.length+existingCount>=maxFiles||(!multiple&&added)){errors.push(f.name+'：超过附件数量限制');continue}
  files.push(f);seen.add(fingerprint(f));added++
 }
 return {files:added?files:current,errors,changed:added>0}
}
