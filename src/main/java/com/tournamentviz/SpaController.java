package com.tournamentviz;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the Vite/React SPA for client-side routes (e.g. {@code /about}) when the app is
 * bundled into {@code classpath:/static/} and run on port 8080.
 */
@Controller
public class SpaController {

  @GetMapping("/about")
  public String about() {
    return "forward:/index.html";
  }
}
