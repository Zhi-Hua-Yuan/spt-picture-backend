package com.spt.sptpicturebackend.manager;

import com.spt.sptpicturebackend.manager.auth.SpaceUserAuthManager;
import com.spt.sptpicturebackend.manager.auth.model.SpaceUserPermissionConstant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpacePermissionRegressionTest {

    private final SpaceUserAuthManager manager = new SpaceUserAuthManager();

    @Test
    void viewerCannotUploadOrDeleteWhileEditorCanEdit() {
        assertThat(manager.getPermissionsByRole("viewer"))
                .contains(SpaceUserPermissionConstant.PICTURE_VIEW)
                .doesNotContain(SpaceUserPermissionConstant.PICTURE_UPLOAD,
                        SpaceUserPermissionConstant.PICTURE_DELETE);
        assertThat(manager.getPermissionsByRole("editor"))
                .contains(SpaceUserPermissionConstant.PICTURE_EDIT);
    }
}
