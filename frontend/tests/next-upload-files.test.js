import test from 'node:test'
import assert from 'node:assert/strict'
import {collectUploadFiles,formatUploadSize} from '../src/next/upload-files.js'
const f=(name,size=12,type='application/pdf',lastModified=1)=>({name,size,type,lastModified})
const opts={multiple:true,maxFiles:3,existingCount:1,accept:'.pdf,.png',maxBytes:100}
test('append valid files and enforce existing + pending count',()=>{
 const a=f('a.pdf'),b=f('b.png',12,'image/png'),c=f('c.pdf')
 const r=collectUploadFiles([a],[b,c],opts)
 assert.deepEqual(r.files,[a,b]);assert.equal(r.errors.length,1);assert.equal(r.changed,true)
})
test('invalid, duplicate, oversized or empty files keep selection',()=>{
 const a=f('a.pdf')
 const r=collectUploadFiles([a],[a,f('x.exe'),f('big.pdf',101),f('empty.pdf',0)],opts)
 assert.deepEqual(r.files,[a]);assert.equal(r.errors.length,4);assert.equal(r.changed,false)
})
test('single file replaces only on a valid selection',()=>{
 const a=f('a.pdf'),b=f('b.pdf')
 assert.deepEqual(collectUploadFiles([a],[],{accept:'.pdf'}).files,[a])
 assert.deepEqual(collectUploadFiles([a],[f('invalid.exe')],{accept:'.pdf'}).files,[a])
 assert.deepEqual(collectUploadFiles([a],[b],{accept:'.pdf'}).files,[b])
})
test('per-format limit and MIME wildcard',()=>{
 const maxBytes=x=>x.name.endsWith('.docx')?100:25
 const r=collectUploadFiles([],[f('a.pdf',30),f('b.docx',80)],{multiple:true,maxFiles:3,accept:'.pdf,.docx',maxBytes})
 assert.deepEqual(r.files,[f('b.docx',80)]);assert.equal(r.errors.length,1)
 assert.equal(collectUploadFiles([],[f('x',10,'image/png')],{accept:'image/*'}).changed,true)
 assert.equal(formatUploadSize(1024),'1.0 KB')
})
