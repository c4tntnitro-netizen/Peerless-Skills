# Character-screen icon

Starsector caches the character-screen skill button's texture. Changing a skill specification after that button is built does not replace its image. The renderer temporarily substitutes the authored 64×64 icon in the cached texture during the player's character-screen render pass and restores the original pixels afterward.

The hook uses public campaign rendering callbacks and LWJGL. Only trained, currently Elite Helmsmanship is eligible. It saves and restores GL pixel-transfer, buffer and texture state, rejects incompatible texture layouts, and keeps interrupted cleanup associated with the original context. The native file loader uses automatically generated mipmaps; the upload and restoration regenerate the lower levels. Manually maintained mip chains are left alone.

The renderer moved from Chief Navigator without a change to the GL code. The native framebuffer regression checks all seven mip levels, restoration across 100 frames, the cached game button, error/cleanup paths and all fourteen authored images. Escape can briefly show the original icon during the screen transition.
