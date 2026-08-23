{ pkgs ? import <nixpkgs> {} }: pkgs.mkShell {
  packages = [ pkgs.temurin-bin-17 pkgs.unzip ];
}
