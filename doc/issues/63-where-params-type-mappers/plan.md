# Plan — Issue #63 : FindQuery doit utiliser les TypeMappers pour les paramètres WHERE

## Contexte

`FindQuery.getParameters()` retourne actuellement les valeurs brutes des `Condition` sans aucune conversion via TypeMapper. Cela provoque des erreurs silencieuses quand une colonne WHERE contient une valeur qui doit être convertie avant d'aller en base (ex. : un enum stocké comme `INTEGER`, un `Boolean` stocké comme `0`/`1`, etc.).

`GenericRepository` dispose déjà de la logique nécessaire (`convertToSqlValue` / `convertParamsToSqlValues`), mais `FindQuery.getParameters()` ne l'utilise pas.

## Règle de conversion

| Situation | Comportement |
|---|---|
| La colonne est trouvée dans `repository.getEntityFields()` | Résoudre le TypeMapper via le dialecte et appeler `toDatabase()` |
| La colonne **n'est pas** trouvée dans `entityFields` (JOIN, alias…) | Laisser la valeur brute + ajouter un `// TODO` explicite |

La présence dans `entityFields` est l'équivalent du "on a un getter" : c'est la seule façon dont FindQuery connaît le type Java déclaré et les annotations `@Type` nécessaires à la résolution du mapper.

## Fichiers concernés

- [FindQuery.java](../../nativsql-core/src/main/java/ovh/heraud/nativsql/util/FindQuery.java) — modification principale
- [GenericRepository.java](../../nativsql-core/src/main/java/ovh/heraud/nativsql/repository/GenericRepository.java) — vérifier l'accessibilité de `getDatabaseDialect()` (déjà public)
- Tests : chercher/créer un test couvrant `getParameters()` avec TypeMapper

## Étapes

### Étape 1 — Exposer `getDatabaseDialect()` si nécessaire

Vérifier que `GenericRepository.getDatabaseDialect()` est bien `public`. D'après la lecture du code, c'est déjà le cas. `FindQuery` a déjà `repository` comme champ, donc aucun changement de visibilité n'est a priori nécessaire.

### Étape 2 — Modifier `FindQuery.getParameters()`

Remplacer la boucle naïve :

```java
// AVANT
public Map<String, Object> getParameters() {
    Map<String, Object> params = new HashMap<>();
    for (Condition condition : whereClause.getConditions()) {
        params.put(condition.getColumn(), condition.getValue());
    }
    return params;
}
```

Par une version qui applique la conversion TypeMapper :

```java
// APRÈS (pseudo-code)
public Map<String, Object> getParameters() {
    Map<String, Object> params = new HashMap<>();
    for (Condition condition : whereClause.getConditions()) {
        String column = condition.getColumn();
        Object value  = condition.getValue();

        FieldAccessor<?> fieldAccessor = repository.getEntityFields().get(column);
        if (fieldAccessor != null) {
            // Récupérer le DbDataType depuis l'annotation @Type (peut être null)
            DbDataType dbDataType = null;
            var typeInfo = annotationManager.getTypeInfo(fieldAccessor);
            if (typeInfo != null) {
                dbDataType = typeInfo.getDataType();
            }
            value = convertConditionValue(value, fieldAccessor, dbDataType);
        } else {
            // TODO: convertir les valeurs pour les colonnes absentes de entityFields
            //       (colonnes de JOIN, aliases, etc.)
        }

        params.put(column, value);
    }
    return params;
}
```

Points d'attention :
- **Liste (opérateur IN)** : si `value instanceof List<?>`, convertir chaque élément séparément avec le même `fieldAccessor` (même pattern que `GenericRepository.convertToSqlValue`).
- **Null** : `convertToSqlValue` retourne déjà `null` si la valeur est nulle — pas de cas particulier.
- **Pas de TypeMapper trouvé** : `databaseDialect.getMapper()` peut retourner `null` pour des types inconnus → lever une `IllegalArgumentException` descriptive (cohérent avec le comportement existant dans `convertToSqlValue`).

### Étape 3 — Méthode helper privée `convertConditionValue`

Extraire la logique dans une petite méthode privée dans `FindQuery` pour garder `getParameters()` lisible. Elle délègue à `repository.getDatabaseDialect()` et `annotationManager` exactement comme `GenericRepository.convertToSqlValue` le fait déjà.

Signature suggérée :

```java
private <V> Object convertConditionValue(V value, FieldAccessor<?> fieldAccessor, DbDataType dbDataType)
```

### Étape 4 — Tests

Identifier les tests existants de `FindQuery` et ajouter des cas :

1. **Getter connu, type simple** : `whereAndEquals(User::getStatus, StatusEnum.ACTIVE)` → la valeur dans `getParameters()` doit être le code SQL de l'enum (String ou Integer selon le dialecte).
2. **Getter connu, boolean stocké en entier** : vérifier que `true` → `1`.
3. **Colonne inconnue** (colonne d'un JOIN) : vérifier que la valeur brute est conservée sans erreur (cas TODO).
4. **Opérateur IN** : `whereAndIn(User::getStatus, List.of(...))` → chaque élément converti.

## Ce qu'on ne fait pas (scope)

- On ne stocke **pas** le `Getter<T>` dans `Condition` : le nom de colonne suffit pour retrouver le `FieldAccessor`.
- On ne traite **pas** les colonnes de JOIN dans ce ticket (TODO explicite dans le code).
- On ne modifie **pas** `WhereClause` ni `Condition`.
