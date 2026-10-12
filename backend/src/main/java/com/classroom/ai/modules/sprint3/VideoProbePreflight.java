package com.classroom.ai.modules.sprint3;
import org.springframework.stereotype.Component;
import org.springframework.boot.*;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.TimeUnit;
@Component @Slf4j
public class VideoProbePreflight implements ApplicationRunner {
 @Value("${classroom.video.ffprobe:ffprobe}") private String tool;
 public void run(ApplicationArguments args){
  try{Process p=new ProcessBuilder(tool,"-version").redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();if(!p.waitFor(3,TimeUnit.SECONDS)){p.destroyForcibly();throw new IllegalStateException("preflight timeout");}if(p.exitValue()!=0)throw new IllegalStateException("preflight failed");log.info("Server video duration probe is available");}
  catch(Exception ex){log.warn("Video probe unavailable: configure CLASSROOM_FFPROBE; video import will return 503");}
 }
}
