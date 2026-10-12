package com.turkerozturk.sweetcherrysync.controllers;

import com.turkerozturk.sweetcherrysync.services.SyncService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NodeControllerTest {
    @Test void manualSyncRequiresPostAndReportsBusyAndFailure() throws Exception {
        SyncService service = mock(SyncService.class);
        when(service.getStatus()).thenReturn(new SyncService.Status("IDLE", null, null, "Idle"));
        MockMvc mvc = standaloneSetup(new NodeController(service)).build();
        mvc.perform(get("/sync")).andExpect(status().isMethodNotAllowed());
        verify(service, never()).syncNodes();
        mvc.perform(get("/sync/status")).andExpect(status().isOk());
        mvc.perform(post("/sync")).andExpect(status().isOk());
        doThrow(new SyncService.SyncBusyException()).when(service).syncNodes();
        mvc.perform(post("/sync")).andExpect(status().isConflict());
        doThrow(new SyncService.SyncSafetyException("Empty source deletion blocked")).when(service).syncNodes();
        mvc.perform(post("/sync")).andExpect(status().isUnprocessableEntity());
        doThrow(new IllegalStateException("private connection detail")).when(service).syncNodes();
        mvc.perform(post("/sync")).andExpect(status().isInternalServerError());
    }
}
