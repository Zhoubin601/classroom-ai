module.exports=async function makeVideo(page){
 await page.bringToFront();
 return page.evaluate(async()=>{
  const c=document.createElement('canvas');c.width=320;c.height=180;c.style.position='fixed';c.style.left='0';c.style.top='0';c.style.zIndex='9999';document.body.append(c);
  const g=c.getContext('2d');g.fillStyle='#2255ee';g.fillRect(0,0,320,180);
  const stream=c.captureStream(0),track=stream.getVideoTracks()[0],r=new MediaRecorder(stream,{mimeType:'video/webm;codecs=vp8'}),parts=[];
  r.ondataavailable=e=>{if(e.data.size)parts.push(e.data)};const done=new Promise(resolve=>r.onstop=resolve);r.start(200);
  for(let n=0;n<40;n++){
   g.fillStyle=n%2?'#cc2233':'#2255ee';g.fillRect(0,0,320,180);g.fillStyle='white';g.font='28px sans-serif';g.fillText('Micro E2E '+n,25,90);track.requestFrame();
   await new Promise(resolve=>setTimeout(resolve,80));
  }
  r.stop();await done;stream.getTracks().forEach(t=>t.stop());c.remove();return Array.from(new Uint8Array(await new Blob(parts,{type:'video/webm'}).arrayBuffer()));
 });
};
