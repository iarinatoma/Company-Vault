package com.example.template.shared.security;

import java.util.UUID;

/** Points at one protected resource: its kind plus its id (a plain UUID, no entity reference). 
 * 
 * Ce face: o „adresă” a unei resurse, 
 * de exemplu „vault-ul cu id-ul X”. O folosim ca să nu trimitem mereu două valori separate.
*/
public record ResourceRef(ResourceType type, UUID id) {}